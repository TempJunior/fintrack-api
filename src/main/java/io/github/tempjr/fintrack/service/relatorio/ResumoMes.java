package io.github.tempjr.fintrack.service.relatorio;

import java.time.YearMonth;
import java.util.List;

/**
 * Um mês na evolução mensal (RF23) ou no comparativo (RF24).
 *
 * @param porCategoria vazio na evolução mensal; preenchido no comparativo
 */
public record ResumoMes(YearMonth mes, ResumoFinanceiro resumo, List<TotalPorCategoria> porCategoria) {

    public ResumoMes {
        porCategoria = List.copyOf(porCategoria);
    }
}
