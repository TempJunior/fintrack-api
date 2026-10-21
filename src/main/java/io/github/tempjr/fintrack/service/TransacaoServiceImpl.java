package io.github.tempjr.fintrack.service;

import io.github.tempjr.fintrack.exception.RecursoNaoEncontradoException;
import io.github.tempjr.fintrack.model.CategorizadorPorRegras;
import io.github.tempjr.fintrack.model.Categoria;
import io.github.tempjr.fintrack.model.Conta;
import io.github.tempjr.fintrack.model.Transacao;
import io.github.tempjr.fintrack.model.enums.OrigemCategoria;
import io.github.tempjr.fintrack.repository.TransacaoFiltro;
import io.github.tempjr.fintrack.repository.TransacaoRepository;
import io.github.tempjr.fintrack.repository.TransacaoSpecs;
import org.springframework.data.domain.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class TransacaoServiceImpl implements TransacaoService {

    private static final Logger log = LoggerFactory.getLogger(TransacaoServiceImpl.class);
    private static final int TAMANHO_LOTE = 500;

    private final TransacaoRepository transacaoRepository;
    private final CategorizacaoService categorizacaoService;

    public TransacaoServiceImpl(TransacaoRepository transacaoRepository, CategorizacaoService categorizacaoService) {
        this.transacaoRepository = transacaoRepository;
        this.categorizacaoService = categorizacaoService;
    }

    @Override
    @Transactional
    public ResultadoDedup salvarNovas(Conta conta, List<Transacao> transacoes) {
        if (transacoes.isEmpty()) {
            return new ResultadoDedup(List.of(), 0);
        }
        validarMesmaConta(conta, transacoes);

        Set<String> chaves = new HashSet<>();
        transacoes.forEach(t -> chaves.add(t.getDedupKey()));
        Set<String> jaGravadas = transacaoRepository.findDedupKeysExistentes(conta.getId(), chaves);

        List<Transacao> novas = new ArrayList<>();
        Set<String> vistasNesteLote = new HashSet<>();
        for (Transacao transacao : transacoes) {
            String chave = transacao.getDedupKey();
            // add() devolve false se a chave já apareceu antes neste mesmo arquivo.
            boolean primeiraVezNoLote = vistasNesteLote.add(chave);
            if (primeiraVezNoLote && !jaGravadas.contains(chave)) {
                novas.add(transacao);
            }
        }

        List<Transacao> gravadas = transacaoRepository.saveAll(novas);
        return new ResultadoDedup(gravadas, transacoes.size() - gravadas.size());
    }

    @Override
    @Transactional
    public ResultadoReaplicacao reaplicarRegras() {
        CategorizadorPorRegras categorizador = categorizacaoService.categorizador();
        int avaliadas = 0;
        int alteradas = 0;

        // Em fatias, para não carregar todas as transações de uma vez (RNF03).
        Pageable pagina = PageRequest.of(0, TAMANHO_LOTE);
        Slice<Transacao> fatia;
        do {
            fatia = transacaoRepository.findCategorizaveis(OrigemCategoria.REGRA, pagina);
            for (Transacao transacao : fatia) {
                avaliadas++;
                if (reaplicar(transacao, categorizador)) {
                    alteradas++;
                }
            }
            pagina = fatia.nextPageable();
        } while (fatia.hasNext());
        // Sem save(): as transações estão gerenciadas e o Hibernate grava as mudanças no commit.

        log.info("Regras reaplicadas: {} avaliadas, {} alteradas", avaliadas, alteradas);
        return new ResultadoReaplicacao(avaliadas, alteradas);
    }

    @Override
    public Page<Transacao> listar(TransacaoFiltro filtro, Pageable pageable) {
        Page<Transacao> pagina = transacaoRepository.findAll(TransacaoSpecs.com(filtro), pageable);
        // Carrega as tags ainda dentro da transação (open-in-view está desligado). Com o batch fetch,
        // isso é 1 consulta para a página inteira, não 1 por transação.
        pagina.forEach(t -> t.getTags().size());
        return pagina;
    }

    @Override
    public Transacao buscar(Long id) {
        return transacaoRepository.findDetalhadaById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Transação", id));
    }

    @Override
    @Transactional
    public Transacao categorizarManualmente(Long id, Long categoriaId) {
        Transacao transacao = buscar(id);
        transacao.categorizarManualmente(categorizacaoService.buscarCategoria(categoriaId));
        log.info("Transação {} categorizada manualmente na categoria {}", id, categoriaId);
        return transacao;
    }

    @Override
    @Transactional
    public Transacao removerCategoria(Long id) {
        Transacao transacao = buscar(id);
        transacao.removerCategoria();
        return transacao;
    }

    @Override
    @Transactional
    public Transacao enriquecer(Long id, String nota, List<String> tags, Boolean transferenciaInterna) {
        Transacao transacao = buscar(id);
        if (nota != null) {
            transacao.anotar(nota);
        }
        if (tags != null) {
            transacao.substituirTags(tags);
        }
        if (transferenciaInterna != null) {
            transacao.marcarTransferenciaInterna(transferenciaInterna);
        }
        // Os campos brutos (data, valor, descrição) não têm método de alteração: RNF06 garantido pelo modelo.
        return transacao;
    }

    /** @return true se a categoria mudou */
    private static boolean reaplicar(Transacao transacao, CategorizadorPorRegras categorizador) {
        Categoria atual = transacao.getCategoria();
        Optional<Categoria> nova = categorizador.categorizar(transacao.getDescricaoOriginal());
        if (nova.isPresent()) {
            if (atual != null && atual.getId().equals(nova.get().getId())) {
                return false;
            }
            return transacao.categorizarPorRegra(nova.get());
        }
        if (atual != null) {
            transacao.removerCategoria(); // a regra que a categorizou não casa mais
            return true;
        }
        return false;
    }

    /** Defesa contra bug de quem chama: o RF03 exige que toda transação seja de exatamente uma conta. */
    private static void validarMesmaConta(Conta conta, List<Transacao> transacoes) {
        Long contaId = Objects.requireNonNull(conta.getId(), "conta precisa estar gravada");
        // Compara pelo id, não por ==: a mesma conta pode estar em duas instâncias diferentes na memória.
        boolean todasDaConta = transacoes.stream().allMatch(t -> contaId.equals(t.getConta().getId()));
        if (!todasDaConta) {
            throw new IllegalArgumentException("todas as transações precisam ser da conta " + conta.getId());
        }
    }
}
