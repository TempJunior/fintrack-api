package io.github.tempjr.fintrack.service.relatorio;

import java.util.List;

/**
 * Totais de uma categoria raiz, já somando as subcategorias, que vêm detalhadas dentro (RF22).
 *
 * @param categoriaId nulo = "Não categorizado"
 */
public record TotalPorCategoria(Long categoriaId, String nome, ResumoFinanceiro resumo, long quantidade,
                                List<TotalPorCategoria> subcategorias) {

    public TotalPorCategoria {
        subcategorias = List.copyOf(subcategorias);
    }
}
