package io.github.tempjr.fintrack.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de POST /api/v1/categorias (RF15). */
public record CriarCategoriaRequest(
        @NotBlank(message = "é obrigatório")
        @Size(max = 60, message = "deve ter no máximo 60 caracteres")
        String nome,

        @Schema(description = "Id da categoria raiz, para criar uma subcategoria. Ausente = categoria raiz.")
        Long categoriaPaiId
) {
}
