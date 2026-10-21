package io.github.tempjr.fintrack.dto.request;

import io.github.tempjr.fintrack.model.enums.Instituicao;
import io.github.tempjr.fintrack.model.enums.TipoConta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corpo de POST /api/v1/contas (RF01). A moeda é sempre BRL (P03). */
public record CriarContaRequest(
        @NotNull(message = "é obrigatória")
        Instituicao instituicao,

        @NotNull(message = "é obrigatório")
        TipoConta tipo,

        @NotBlank(message = "é obrigatório")
        @Size(max = 60, message = "deve ter no máximo 60 caracteres")
        String apelido
) {
}
