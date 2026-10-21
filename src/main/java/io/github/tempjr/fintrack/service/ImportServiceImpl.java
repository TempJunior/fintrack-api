package io.github.tempjr.fintrack.service;

import io.github.tempjr.fintrack.exception.ArquivoInvalidoException;
import io.github.tempjr.fintrack.exception.ImportacaoRejeitadaException;
import io.github.tempjr.fintrack.exception.RecursoNaoEncontradoException;
import io.github.tempjr.fintrack.model.Conta;
import io.github.tempjr.fintrack.model.Importacao;
import io.github.tempjr.fintrack.model.Transacao;
import io.github.tempjr.fintrack.model.enums.StatementFormat;
import io.github.tempjr.fintrack.model.enums.TipoConta;
import io.github.tempjr.fintrack.parser.AccountRef;
import io.github.tempjr.fintrack.parser.FormatDetector;
import io.github.tempjr.fintrack.parser.ImportContext;
import io.github.tempjr.fintrack.parser.ParsedStatement;
import io.github.tempjr.fintrack.parser.RawTransaction;
import io.github.tempjr.fintrack.parser.StatementParserResolver;
import io.github.tempjr.fintrack.repository.ImportacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Orquestra o Fluxo B: detectar formato → parse → validar conta → dedup + gravar → auditoria.
 * <p>
 * Transações controladas com {@link TransactionTemplate} (e não {@code @Transactional}) porque a
 * falha precisa ser registrada <b>depois</b> do rollback, numa transação própria. Com
 * {@code @Transactional} no método, o registro da falha seria desfeito junto com o resto.
 */
@Service
public class ImportServiceImpl implements ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportServiceImpl.class);
    private static final int TAMANHO_MAXIMO_NOME = 255;

    private final ContaService contaService;
    private final TransacaoService transacaoService;
    private final ImportacaoRepository importacaoRepository;
    private final FormatDetector formatDetector;
    private final StatementParserResolver parserResolver;
    private final TransactionTemplate transactionTemplate;

    public ImportServiceImpl(ContaService contaService,
                             TransacaoService transacaoService,
                             ImportacaoRepository importacaoRepository,
                             FormatDetector formatDetector,
                             StatementParserResolver parserResolver,
                             TransactionTemplate transactionTemplate) {
        this.contaService = contaService;
        this.transacaoService = transacaoService;
        this.importacaoRepository = importacaoRepository;
        this.formatDetector = formatDetector;
        this.parserResolver = parserResolver;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public Importacao importar(Long contaId, String nomeArquivo, byte[] conteudo) {
        // Conta inexistente: 404 sem auditoria (não há conta para vincular o registro).
        Conta conta = contaService.buscarPorId(contaId);
        String nome = normalizarNome(nomeArquivo);
        StatementFormat formato = null;
        try {
            if (!conta.isAtiva()) {
                throw new ImportacaoRejeitadaException("A conta está inativa e não pode receber importações.");
            }
            // 1–2. Fora de transação: nada é gravado até o arquivo inteiro ser lido.
            formato = formatDetector.detectar(conteudo);
            ImportContext ctx = new ImportContext(conta.getInstituicao(), formato, nome);
            ParsedStatement extrato = parserResolver.resolver(ctx).parse(new ByteArrayInputStream(conteudo), ctx);

            // 3–6. Numa transação só: tudo ou nada (RNF05, arquitetura §7.5).
            StatementFormat formatoDetectado = formato;
            Importacao importacao = transactionTemplate.execute(
                    status -> persistir(contaId, nome, formatoDetectado, extrato));

            // Só ids e contagens no log (RNF08). O nome do arquivo pode conter o número da conta.
            log.info("Importação {} na conta {}: {} lidas, {} novas, {} duplicadas",
                    importacao.getId(), contaId, importacao.getTotalLido(), importacao.getNovas(),
                    importacao.getDuplicadas());
            return importacao;
        } catch (RuntimeException e) {
            registrarFalha(contaId, nome, formato, e);
            throw e;
        }
    }

    private Importacao persistir(Long contaId, String nome, StatementFormat formato, ParsedStatement extrato) {
        Conta conta = contaService.buscarPorId(contaId); // gerenciada nesta transação
        validarContaDeclarada(conta, extrato.declaredAccount());

        List<Transacao> transacoes = converter(conta, extrato.transactions());
        ResultadoDedup resultado = transacaoService.salvarNovas(conta, transacoes);
        // TODO Etapa 5: aplicar as regras de categorização nas novas (RF17).

        return importacaoRepository.save(Importacao.sucesso(conta, nome, formato,
                resultado.totalLido(), resultado.novas().size(), resultado.duplicadas(),
                normalizarSaldo(extrato.ledgerBalance()), extrato.dataSaldo()));
    }

    /**
     * Confere se o arquivo é mesmo da conta escolhida (arquitetura §7.4, D-09, D-13).
     * Mensagens sem número de conta (RNF08).
     */
    private static void validarContaDeclarada(Conta conta, AccountRef declarada) {
        boolean contaDeCartao = conta.getTipo() == TipoConta.CARTAO;
        if (declarada.cartaoDeCredito() && !contaDeCartao) {
            throw new ImportacaoRejeitadaException(
                    "O arquivo é um extrato de cartão de crédito, mas a conta escolhida não é de cartão.");
        }
        if (!declarada.cartaoDeCredito() && contaDeCartao) {
            throw new ImportacaoRejeitadaException(
                    "O arquivo é um extrato de conta, mas a conta escolhida é de cartão de crédito.");
        }
        if (declarada.bankId() != null && !mesmoBanco(declarada.bankId(), conta.getInstituicao().getCodigo())) {
            throw new ImportacaoRejeitadaException(
                    "O arquivo é de outro banco, não de " + conta.getInstituicao() + ".");
        }
        if (!conta.conferirOuVincularNumeroExterno(declarada.acctId())) {
            throw new ImportacaoRejeitadaException(
                    "O arquivo é de outra conta. Confira se escolheu a conta certa.");
        }
    }

    /** "0341" e "341" são o mesmo banco. */
    private static boolean mesmoBanco(String a, String b) {
        return semZerosAEsquerda(a).equals(semZerosAEsquerda(b));
    }

    private static String semZerosAEsquerda(String codigo) {
        String s = codigo.strip().replaceFirst("^0+(?!$)", "");
        return s.isEmpty() ? "0" : s;
    }

    private static List<Transacao> converter(Conta conta, List<RawTransaction> brutas) {
        List<Transacao> transacoes = new ArrayList<>(brutas.size());
        for (int i = 0; i < brutas.size(); i++) {
            RawTransaction t = brutas.get(i);
            try {
                transacoes.add(new Transacao(conta, t.providerTransactionId(), t.date(), t.amount(), t.description()));
            } catch (IllegalArgumentException e) {
                throw new ArquivoInvalidoException("Transação nº " + (i + 1) + " do arquivo tem valor inválido.", e);
            }
        }
        return transacoes;
    }

    private static BigDecimal normalizarSaldo(BigDecimal saldo) {
        if (saldo == null) {
            return null;
        }
        try {
            return saldo.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new ArquivoInvalidoException("O saldo declarado no arquivo tem valor inválido.", e);
        }
    }

    /**
     * Registra a tentativa que falhou numa transação própria, depois do rollback (RNF12).
     * Se nem isso der certo, a falha original é a que importa: ela segue para o usuário.
     */
    private void registrarFalha(Long contaId, String nome, StatementFormat formato, RuntimeException causa) {
        try {
            transactionTemplate.executeWithoutResult(status ->
                    importacaoRepository.save(Importacao.falha(contaService.buscarPorId(contaId), nome, formato, 1)));
        } catch (RuntimeException erroAoRegistrar) {
            causa.addSuppressed(erroAoRegistrar);
            log.error("Não foi possível registrar a falha da importação na conta {}", contaId, erroAoRegistrar);
        }
        if (causa instanceof ImportacaoRejeitadaException) {
            // Mensagens de rejeição são nossas e não têm dado sensível.
            log.warn("Importação rejeitada na conta {}: {}", contaId, causa.getMessage());
        }
    }

    /** Navegadores antigos mandam o caminho inteiro ("C:\...\extrato.ofx"); fica só o nome. */
    static String normalizarNome(String nomeArquivo) {
        if (nomeArquivo == null || nomeArquivo.isBlank()) {
            return "extrato.ofx";
        }
        String nome = nomeArquivo.replace('\\', '/');
        nome = nome.substring(nome.lastIndexOf('/') + 1).strip();
        if (nome.isEmpty()) {
            return "extrato.ofx";
        }
        return nome.length() > TAMANHO_MAXIMO_NOME ? nome.substring(0, TAMANHO_MAXIMO_NOME) : nome;
    }

    @Override
    public List<Importacao> listar(Long contaId) {
        contaService.buscarPorId(contaId); // 404 se a conta não existir
        return importacaoRepository.findByContaIdOrderByDataHoraDescIdDesc(contaId);
    }

    @Override
    public Importacao buscar(Long contaId, Long importacaoId) {
        return importacaoRepository.findByIdAndContaId(importacaoId, contaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Importação", importacaoId));
    }
}
