package io.github.tempjr.fintrack.dto.response;

import io.github.tempjr.fintrack.model.RegraCategorizacao;
import io.github.tempjr.fintrack.model.enums.Operador;

/** Regra de categorização (RF16), com o nome da categoria para a tela não precisar de outra chamada. */
public record RegraResponse(Long id, Long categoriaId, String categoriaNome, Operador operador, String padrao,
                            int prioridade) {

    /** A categoria precisa estar carregada (o repository usa JOIN FETCH). */
    public static RegraResponse from(RegraCategorizacao regra) {
        return new RegraResponse(
                regra.getId(),
                regra.getCategoria().getId(),
                regra.getCategoria().getNome(),
                regra.getOperador(),
                regra.getPadrao(),
                regra.getPrioridade());
    }
}
