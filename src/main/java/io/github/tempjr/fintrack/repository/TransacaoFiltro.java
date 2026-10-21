package io.github.tempjr.fintrack.repository;

import io.github.tempjr.fintrack.model.enums.TipoTransacao;

import java.time.LocalDate;

/**
 * Filtros combináveis da listagem de transações (RF12). Campo nulo = não filtra.
 *
 * @param categoriaId  inclui as subcategorias da categoria informada
 * @param semCategoria true = só "Não categorizado" (RF20)
 * @param texto        busca na descrição, sem diferenciar maiúsculas
 */
public record TransacaoFiltro(
        Long contaId,
        Long categoriaId,
        boolean semCategoria,
        TipoTransacao tipo,
        LocalDate dataInicio,
        LocalDate dataFim,
        String texto
) {

    public TransacaoFiltro {
        if (dataInicio != null && dataFim != null && dataInicio.isAfter(dataFim)) {
            throw new IllegalArgumentException("dataInicio não pode ser depois de dataFim");
        }
        if (semCategoria && categoriaId != null) {
            throw new IllegalArgumentException("use categoriaId ou semCategoria, não os dois");
        }
        texto = (texto == null || texto.isBlank()) ? null : texto.strip();
    }

    public static TransacaoFiltro vazio() {
        return new TransacaoFiltro(null, null, false, null, null, null, null);
    }
}
