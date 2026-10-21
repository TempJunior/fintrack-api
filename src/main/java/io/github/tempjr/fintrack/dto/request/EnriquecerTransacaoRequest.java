package io.github.tempjr.fintrack.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Corpo de PATCH /api/v1/transacoes/{id} (RF13, RF14). Campo ausente = não muda.
 * Não há campos de data, valor ou descrição: o dado bruto é imutável (RF11, RNF06).
 */
public record EnriquecerTransacaoRequest(
        @Size(max = 500, message = "deve ter no máximo 500 caracteres")
        @Schema(description = "Texto vazio apaga a nota")
        String nota,

        @Size(max = 20, message = "deve ter no máximo 20 tags")
        @Schema(description = "Substitui todas as tags; lista vazia apaga")
        List<@NotBlank(message = "não pode ter tag vazia") @Size(max = 40, message = "tag deve ter no máximo 40 caracteres") String> tags,

        @Schema(description = "true = não conta como receita nem despesa nos relatórios (RF14)")
        Boolean transferenciaInterna
) {
}
