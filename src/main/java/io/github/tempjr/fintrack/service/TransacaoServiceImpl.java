package io.github.tempjr.fintrack.service;

import io.github.tempjr.fintrack.model.Conta;
import io.github.tempjr.fintrack.model.Transacao;
import io.github.tempjr.fintrack.repository.TransacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class TransacaoServiceImpl implements TransacaoService {

    private final TransacaoRepository transacaoRepository;

    public TransacaoServiceImpl(TransacaoRepository transacaoRepository) {
        this.transacaoRepository = transacaoRepository;
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
