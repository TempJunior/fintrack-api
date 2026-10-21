package io.github.tempjr.fintrack.service;

import io.github.tempjr.fintrack.model.Transacao;

import java.util.List;

/**
 * Resultado de {@link TransacaoService#salvarNovas}: as transações gravadas e as contagens
 * que vão para o resumo da importação (RF08).
 *
 * @param novas       transações gravadas agora (com id)
 * @param duplicadas  quantas foram ignoradas por já existirem na conta ou por repetirem no próprio arquivo
 */
public record ResultadoDedup(List<Transacao> novas, int duplicadas) {

    public ResultadoDedup {
        novas = List.copyOf(novas);
    }

    public int totalLido() {
        return novas.size() + duplicadas;
    }
}
