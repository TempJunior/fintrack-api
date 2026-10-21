package io.github.tempjr.fintrack.service;

/**
 * Resultado de reaplicar as regras em lote (RF19).
 *
 * @param avaliadas transações sem categoria ou categorizadas por regra (as manuais nem entram)
 * @param alteradas quantas mudaram de categoria (inclusive as que voltaram a "Não categorizado")
 */
public record ResultadoReaplicacao(int avaliadas, int alteradas) {
}
