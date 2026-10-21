package io.github.tempjr.fintrack.repository;

import java.time.LocalDate;

/**
 * Filtros comuns dos relatórios (RF26). Campo nulo = não filtra.
 *
 * @param categoriaId inclui as subcategorias
 */
public record FiltroRelatorio(Long contaId, Long categoriaId, LocalDate dataInicio, LocalDate dataFim) {

    public FiltroRelatorio {
        if (dataInicio != null && dataFim != null && dataInicio.isAfter(dataFim)) {
            throw new IllegalArgumentException("dataInicio não pode ser depois de dataFim");
        }
    }

    /** Mesmo filtro, restrito a outro período (usado para fatiar por mês). */
    public FiltroRelatorio noPeriodo(LocalDate inicio, LocalDate fim) {
        return new FiltroRelatorio(contaId, categoriaId, inicio, fim);
    }
}
