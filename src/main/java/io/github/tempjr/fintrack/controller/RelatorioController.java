package io.github.tempjr.fintrack.controller;

import io.github.tempjr.fintrack.dto.response.RelatorioResponses.CategoriaTotalResponse;
import io.github.tempjr.fintrack.dto.response.RelatorioResponses.MesResponse;
import io.github.tempjr.fintrack.dto.response.RelatorioResponses.ResumoResponse;
import io.github.tempjr.fintrack.dto.response.RelatorioResponses.SaldosResponse;
import io.github.tempjr.fintrack.repository.FiltroRelatorio;
import io.github.tempjr.fintrack.service.RelatorioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Relatórios e dashboards (RF21–RF26). Somente leitura; transferências internas ficam fora (RF14). */
@Tag(name = "Relatórios", description = "Receitas × despesas, por categoria, mensal, comparativo e saldos (RF21–RF26)")
@RestController
@RequestMapping("/api/v1/relatorios")
public class RelatorioController {

    private final RelatorioService relatorioService;

    public RelatorioController(RelatorioService relatorioService) {
        this.relatorioService = relatorioService;
    }

    @Operation(summary = "Receitas, despesas e saldo do período (RF21)")
    @GetMapping("/resumo")
    public ResumoResponse resumo(
            @RequestParam(required = false) Long contaId,
            @Parameter(description = "Inclui as subcategorias") @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
        return ResumoResponse.from(relatorioService.resumo(new FiltroRelatorio(contaId, categoriaId, dataInicio, dataFim)));
    }

    @Operation(summary = "Totais por categoria (RF22)",
            description = "Por categoria raiz (somando as subcategorias, que vêm dentro), mais despesas primeiro. "
                    + "\"Não categorizado\" vem por último.")
    @GetMapping("/por-categoria")
    public List<CategoriaTotalResponse> porCategoria(
            @RequestParam(required = false) Long contaId,
            @Parameter(description = "Inclui as subcategorias") @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
        return relatorioService.porCategoria(new FiltroRelatorio(contaId, categoriaId, dataInicio, dataFim)).stream()
                .map(CategoriaTotalResponse::from)
                .toList();
    }

    @Operation(summary = "Evolução mensal de receitas e despesas (RF23)",
            description = "Um item por mês, inclusive meses sem movimento. Máximo de 36 meses.")
    @GetMapping("/mensal")
    public List<MesResponse> mensal(
            @Parameter(description = "yyyy-MM", example = "2026-01") @RequestParam YearMonth mesInicio,
            @Parameter(description = "yyyy-MM", example = "2026-06") @RequestParam YearMonth mesFim,
            @RequestParam(required = false) Long contaId,
            @RequestParam(required = false) Long categoriaId) {
        return relatorioService.evolucaoMensal(mesInicio, mesFim, contaId, categoriaId).stream()
                .map(MesResponse::from)
                .toList();
    }

    @Operation(summary = "Comparativo entre meses (RF24)",
            description = "De 2 a 12 meses, com totais e categorias de cada um.")
    @GetMapping("/comparativo")
    public List<MesResponse> comparativo(
            @Parameter(description = "Meses yyyy-MM separados por vírgula", example = "2026-05,2026-06")
            @RequestParam List<YearMonth> meses,
            @RequestParam(required = false) Long contaId,
            @RequestParam(required = false) Long categoriaId) {
        return relatorioService.comparativo(meses, contaId, categoriaId).stream()
                .map(MesResponse::from)
                .toList();
    }

    @Operation(summary = "Saldo por conta e consolidado (RF25)",
            description = "Saldo informado pelo banco no extrato mais recente de cada conta ativa, com a data.")
    @GetMapping("/saldos")
    public SaldosResponse saldos() {
        return SaldosResponse.from(relatorioService.saldos());
    }
}
