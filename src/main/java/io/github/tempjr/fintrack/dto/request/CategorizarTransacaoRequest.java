package io.github.tempjr.fintrack.dto.request;

import jakarta.validation.constraints.NotNull;

/** Corpo de PUT /api/v1/transacoes/{id}/categoria (RF18). */
public record CategorizarTransacaoRequest(
        @NotNull(message = "é obrigatória")
        Long categoriaId
) {
}
