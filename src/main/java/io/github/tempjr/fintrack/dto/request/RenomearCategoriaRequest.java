package io.github.tempjr.fintrack.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de PATCH /api/v1/categorias/{id}. */
public record RenomearCategoriaRequest(
        @NotBlank(message = "é obrigatório")
        @Size(max = 60, message = "deve ter no máximo 60 caracteres")
        String nome
) {
}
