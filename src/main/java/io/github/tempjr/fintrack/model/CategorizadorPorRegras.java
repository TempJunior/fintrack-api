package io.github.tempjr.fintrack.model;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Decide a categoria de uma descrição a partir de um conjunto de regras (RF17, RF19).
 * Java puro: recebe as regras prontas e não sabe de banco. A primeira regra que casa vence,
 * em ordem de prioridade (menor primeiro) e, no empate, de criação (id menor primeiro).
 */
public final class CategorizadorPorRegras {

    private static final Comparator<RegraCategorizacao> ORDEM =
            Comparator.comparingInt(RegraCategorizacao::getPrioridade)
                    .thenComparing(RegraCategorizacao::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final List<RegraCategorizacao> regras;

    public CategorizadorPorRegras(List<RegraCategorizacao> regras) {
        this.regras = regras.stream().sorted(ORDEM).toList();
    }

    public Optional<Categoria> categorizar(String descricao) {
        return regras.stream()
                .filter(regra -> regra.corresponde(descricao))
                .findFirst()
                .map(RegraCategorizacao::getCategoria);
    }

    public boolean semRegras() {
        return regras.isEmpty();
    }
}
