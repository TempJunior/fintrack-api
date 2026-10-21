package io.github.tempjr.fintrack.dto.response;

import io.github.tempjr.fintrack.model.Importacao;
import io.github.tempjr.fintrack.model.enums.ResultadoImportacao;
import io.github.tempjr.fintrack.model.enums.StatementFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Resumo de uma importação (RF08) e item do histórico (RNF12).
 * Dinheiro sai como string decimal ("-123.45"), nunca como número JSON (arquitetura §8.3, ADR-003).
 */
public record ImportacaoResponse(
        Long id,
        String nomeArquivo,
        @Schema(description = "Nulo quando o formato não foi reconhecido")
        StatementFormat formato,
        Instant dataHora,
        int totalLido,
        int novas,
        int duplicadas,
        int erros,
        ResultadoImportacao resultado,
        @Schema(type = "string", example = "4807.15", description = "Saldo informado pelo banco no arquivo (LEDGERBAL)")
        String saldoDeclarado,
        @Schema(description = "Data a que o saldo declarado se refere")
        LocalDate dataSaldo
) {

    public static ImportacaoResponse from(Importacao importacao) {
        return new ImportacaoResponse(
                importacao.getId(),
                importacao.getNomeArquivo(),
                importacao.getFormato(),
                importacao.getDataHora(),
                importacao.getTotalLido(),
                importacao.getNovas(),
                importacao.getDuplicadas(),
                importacao.getErros(),
                importacao.getResultado(),
                decimal(importacao.getSaldoDeclarado()),
                importacao.getDataSaldo());
    }

    private static String decimal(BigDecimal valor) {
        return valor == null ? null : valor.toPlainString();
    }
}
