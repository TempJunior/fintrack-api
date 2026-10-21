package io.github.tempjr.fintrack.service.relatorio;

import java.math.BigDecimal;

/**
 * Receitas e despesas de um recorte (RF21). Despesas em valor positivo; saldo = receitas − despesas.
 * Transferências internas já vêm excluídas (RF14).
 */
public record ResumoFinanceiro(BigDecimal receitas, BigDecimal despesas) {

    public static final ResumoFinanceiro ZERADO =
            new ResumoFinanceiro(BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));

    public BigDecimal saldo() {
        return receitas.subtract(despesas);
    }

    public ResumoFinanceiro somar(ResumoFinanceiro outro) {
        return new ResumoFinanceiro(receitas.add(outro.receitas), despesas.add(outro.despesas));
    }
}
