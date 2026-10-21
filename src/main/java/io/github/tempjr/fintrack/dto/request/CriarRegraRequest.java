package io.github.tempjr.fintrack.dto.request;

import io.github.tempjr.fintrack.model.enums.Operador;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corpo de POST /api/v1/regras (RF16). */
public record CriarRegraRequest(
        @NotNull(message = "é obrigatória")
        Long categoriaId,

        @NotNull(message = "é obrigatório")
        Operador operador,

        @NotBlank(message = "é obrigatório")
        @Size(max = 255, message = "deve ter no máximo 255 caracteres")
        @Schema(description = "CONTAINS e STARTS_WITH ignoram maiúsculas e acentos; REGEX ignora maiúsculas",
                example = "padaria")
        String padrao,

        @NotNull(message = "é obrigatória")
        @Min(value = 0, message = "não pode ser negativa")
        @Schema(description = "Menor é avaliada primeiro; a primeira regra que casa vence", example = "10")
        Integer prioridade
) {
}
