package io.github.tempjr.fintrack.dto.response;

import io.github.tempjr.fintrack.model.enums.TipoConta;
import io.github.tempjr.fintrack.service.relatorio.PanoramaSaldos;
import io.github.tempjr.fintrack.service.relatorio.ResumoFinanceiro;
import io.github.tempjr.fintrack.service.relatorio.ResumoMes;
import io.github.tempjr.fintrack.service.relatorio.TotalPorCategoria;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Respostas dos relatórios (RF21–RF25). Dinheiro como string decimal (arquitetura §8.3);
 * despesas em valor positivo; saldo = receitas − despesas.
 */
public final class RelatorioResponses {

    private RelatorioResponses() {
    }

    static String decimal(BigDecimal valor) {
        return valor == null ? null : valor.toPlainString();
    }

    public record ResumoResponse(
            @Schema(type = "string", example = "3589.90") String receitas,
            @Schema(type = "string", example = "282.75", description = "Valor positivo") String despesas,
            @Schema(type = "string", example = "3307.15", description = "receitas − despesas") String saldo) {

        public static ResumoResponse from(ResumoFinanceiro r) {
            return new ResumoResponse(decimal(r.receitas()), decimal(r.despesas()), decimal(r.saldo()));
        }
    }

    public record CategoriaTotalResponse(
            @Schema(description = "Nulo = Não categorizado") Long categoriaId,
            String nome,
            @Schema(type = "string") String receitas,
            @Schema(type = "string", description = "Valor positivo") String despesas,
            long quantidade,
            List<CategoriaTotalResponse> subcategorias) {

        public static CategoriaTotalResponse from(TotalPorCategoria t) {
            return new CategoriaTotalResponse(t.categoriaId(), t.nome(),
                    decimal(t.resumo().receitas()), decimal(t.resumo().despesas()), t.quantidade(),
                    t.subcategorias().stream().map(CategoriaTotalResponse::from).toList());
        }
    }

    public record MesResponse(
            @Schema(example = "2026-06") String mes,
            @Schema(type = "string") String receitas,
            @Schema(type = "string") String despesas,
            @Schema(type = "string") String saldo,
            @Schema(description = "Preenchido só no comparativo") List<CategoriaTotalResponse> porCategoria) {

        public static MesResponse from(ResumoMes m) {
            return new MesResponse(m.mes().toString(),
                    decimal(m.resumo().receitas()), decimal(m.resumo().despesas()), decimal(m.resumo().saldo()),
                    m.porCategoria().stream().map(CategoriaTotalResponse::from).toList());
        }
    }

    public record SaldosResponse(
            @Schema(type = "string", description = "Soma dos saldos conhecidos (cartão entra negativo)") String consolidado,
            List<SaldoContaResponse> contas) {

        public static SaldosResponse from(PanoramaSaldos p) {
            return new SaldosResponse(decimal(p.consolidado()),
                    p.contas().stream().map(SaldoContaResponse::from).toList());
        }
    }

    public record SaldoContaResponse(
            Long contaId,
            String apelido,
            TipoConta tipo,
            @Schema(type = "string", description = "Nulo se a conta nunca foi importada") String saldo,
            @Schema(description = "Data a que o saldo se refere (do extrato)") LocalDate dataSaldo) {

        static SaldoContaResponse from(PanoramaSaldos.SaldoConta s) {
            return new SaldoContaResponse(s.contaId(), s.apelido(), s.tipo(), decimal(s.saldo()), s.dataSaldo());
        }
    }
}
