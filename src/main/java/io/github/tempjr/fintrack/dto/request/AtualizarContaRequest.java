package io.github.tempjr.fintrack.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de PATCH /api/v1/contas/{id}. Só o apelido é editável (instituição e tipo são fixos). */
public record AtualizarContaRequest(
        @NotBlank(message = "é obrigatório")
        @Size(max = 60, message = "deve ter no máximo 60 caracteres")
        String apelido
) {
}
