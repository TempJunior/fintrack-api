package io.github.tempjr.fintrack.dto.response;

import io.github.tempjr.fintrack.model.Categoria;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Categoria com suas subcategorias (RF15). Na listagem, só as raízes ficam no topo.
 *
 * @param categoriaPaiId nulo para categoria raiz
 */
public record CategoriaResponse(Long id, String nome, Long categoriaPaiId, List<CategoriaResponse> subcategorias) {

    public static CategoriaResponse from(Categoria categoria) {
        return new CategoriaResponse(categoria.getId(), categoria.getNome(), paiId(categoria), List.of());
    }

    /**
     * Monta a árvore a partir da lista plana (já ordenada por nome), em memória.
     * Uma consulta para todas as categorias, em vez de uma por categoria raiz (evita N+1).
     */
    public static List<CategoriaResponse> arvore(List<Categoria> todas) {
        Map<Long, List<CategoriaResponse>> filhasPorPai = new LinkedHashMap<>();
        for (Categoria c : todas) {
            Long pai = paiId(c);
            if (pai != null) {
                filhasPorPai.computeIfAbsent(pai, k -> new ArrayList<>()).add(from(c));
            }
        }
        return todas.stream()
                .filter(c -> !c.isSubcategoria())
                .map(raiz -> new CategoriaResponse(raiz.getId(), raiz.getNome(), null,
                        List.copyOf(filhasPorPai.getOrDefault(raiz.getId(), List.of()))))
                .toList();
    }

    private static Long paiId(Categoria categoria) {
        return categoria.getPai() == null ? null : categoria.getPai().getId();
    }
}
