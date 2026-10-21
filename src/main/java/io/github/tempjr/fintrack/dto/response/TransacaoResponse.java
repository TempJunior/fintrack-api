package io.github.tempjr.fintrack.dto.response;

import io.github.tempjr.fintrack.model.Transacao;
import io.github.tempjr.fintrack.model.enums.OrigemCategoria;
import io.github.tempjr.fintrack.model.enums.TipoTransacao;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * Transação na API: dado bruto do banco (somente leitura) + enriquecimento.
 * Dinheiro como string decimal (arquitetura §8.3, ADR-003).
 */
public record TransacaoResponse(
        Long id,
        Long contaId,
        String contaApelido,
        LocalDate data,
        @Schema(type = "string", example = "-150.75", description = "Negativo = débito")
        String valor,
        TipoTransacao tipo,
        @Schema(description = "Descrição original do banco; não pode ser alterada")
        String descricao,
        @Schema(description = "Nulo = Não categorizado")
        Long categoriaId,
        String categoriaNome,
        @Schema(description = "MANUAL (escolhida pelo usuário) ou REGRA; nulo se sem categoria")
        OrigemCategoria origemCategoria,
        String nota,
        List<String> tags,
        boolean transferenciaInterna
) {

    /** A transação precisa vir com conta, categoria e tags carregadas (o service garante). */
    public static TransacaoResponse from(Transacao t) {
        return new TransacaoResponse(
                t.getId(),
                t.getConta().getId(),
                t.getConta().getApelido(),
                t.getDataPostada(),
                t.getValor().toPlainString(),
                t.getTipo(),
                t.getDescricaoOriginal(),
                t.getCategoria() == null ? null : t.getCategoria().getId(),
                t.getCategoria() == null ? null : t.getCategoria().getNome(),
                t.getOrigemCategoria(),
                t.getNota(),
                t.getTags().stream().sorted().toList(),
                t.isTransferenciaInterna());
    }
}
