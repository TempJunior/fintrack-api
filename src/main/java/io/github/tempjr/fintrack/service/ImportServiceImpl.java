package io.github.tempjr.fintrack.service;

import com.webcohesion.ofx4j.domain.data.MessageSetType;
import com.webcohesion.ofx4j.domain.data.ResponseEnvelope;
import com.webcohesion.ofx4j.domain.data.ResponseMessage;
import com.webcohesion.ofx4j.domain.data.ResponseMessageSet;
import com.webcohesion.ofx4j.domain.data.banking.BankStatementResponseTransaction;
import com.webcohesion.ofx4j.domain.data.banking.BankingResponseMessageSet;
import com.webcohesion.ofx4j.domain.data.common.Transaction;
import com.webcohesion.ofx4j.io.AggregateUnmarshaller;
import io.github.tempjr.fintrack.model.Conta;
import io.github.tempjr.fintrack.model.Transacao;
import io.github.tempjr.fintrack.repository.TransacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ImportServiceImpl implements ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportServiceImpl.class);

    private final ContaService contaService;
    // Temporário: pela regra das camadas, quem usa TransacaoRepository é o TransacaoService (Etapa 3).
    private final TransacaoRepository transacaoRepository;

    public ImportServiceImpl(ContaService contaService, TransacaoRepository transacaoRepository) {
        this.contaService = contaService;
        this.transacaoRepository = transacaoRepository;
    }

    @Override
    @Transactional
    public int importar(Long contaId, File arquivo) {
        if (arquivo == null) {
            throw new IllegalArgumentException("arquivo é obrigatório");
        }
        Conta conta = contaService.buscarPorId(contaId);
        if (!conta.isAtiva()) {
            throw new IllegalArgumentException("conta " + contaId + " está inativa");
        }

        List<Transacao> lidas = lerTransacoes(arquivo, conta);
        List<Transacao> novas = new ArrayList<>();
        Set<String> chavesNoArquivo = new HashSet<>();

        for (Transacao transacao : lidas) {
            boolean repetidaNoArquivo = !chavesNoArquivo.add(transacao.getDedupKey());
            boolean jaGravada = transacaoRepository.existsByContaIdAndDedupKey(contaId, transacao.getDedupKey());
            if (!repetidaNoArquivo && !jaGravada) {
                novas.add(transacao);
            }
        }
        transacaoRepository.saveAll(novas);

        // Só contagens e ids no log (RNF08).
        log.info("Importação conta {}: {} lidas, {} novas, {} duplicadas",
                contaId, lidas.size(), novas.size(), lidas.size() - novas.size());
        return novas.size();
    }

    private List<Transacao> lerTransacoes(File arquivo, Conta conta) {
        List<Transacao> resultado = new ArrayList<>();
        try (InputStream in = new FileInputStream(arquivo)) {
            AggregateUnmarshaller<ResponseEnvelope> unmarshaller = new AggregateUnmarshaller<>(ResponseEnvelope.class);
            ResponseEnvelope envelope = unmarshaller.unmarshal(in);
            ResponseMessageSet messageSet = envelope.getMessageSet(MessageSetType.banking);
            if (messageSet == null) {
                return resultado;
            }
            for (ResponseMessage mensagem : ((BankingResponseMessageSet) messageSet).getResponseMessages()) {
                BankStatementResponseTransaction extrato = (BankStatementResponseTransaction) mensagem;
                for (Transaction t : extrato.getMessage().getTransactionList().getTransactions()) {
                    resultado.add(new Transacao(
                            conta,
                            t.getId(),
                            // TODO Etapa 4: usar a data do arquivo sem depender do fuso da máquina (estado §7, item 7).
                            t.getDatePosted().toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
                            // TODO Etapa 4: evitar a passagem por double (estado §7, item 6).
                            BigDecimal.valueOf(t.getAmount()),
                            t.getMemo() != null ? t.getMemo() : (t.getName() != null ? t.getName() : "")));
                }
            }
        } catch (Exception e) {
            // TODO Etapa 4: exceção própria (ArquivoInvalidoException) com mensagem clara (RF09).
            throw new IllegalStateException("Erro ao ler arquivo OFX", e);
        }
        return resultado;
    }
}
