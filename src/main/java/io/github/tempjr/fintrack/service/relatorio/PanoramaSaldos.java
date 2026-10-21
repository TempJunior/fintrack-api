package io.github.tempjr.fintrack.service.relatorio;

import io.github.tempjr.fintrack.model.enums.TipoConta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Saldos das contas ativas e o consolidado (RF25, D-10).
 *
 * @param consolidado soma dos saldos conhecidos; contas sem importação ficam de fora
 */
public record PanoramaSaldos(BigDecimal consolidado, List<SaldoConta> contas) {

    public PanoramaSaldos {
        contas = List.copyOf(contas);
    }

    /**
     * @param saldo     LEDGERBAL da importação mais recente (pela data do saldo); nulo se a conta nunca
     *                  foi importada. Cartão costuma ser negativo (é dívida).
     * @param dataSaldo data a que o saldo se refere
     */
    public record SaldoConta(Long contaId, String apelido, TipoConta tipo, BigDecimal saldo, LocalDate dataSaldo) {
    }
}
