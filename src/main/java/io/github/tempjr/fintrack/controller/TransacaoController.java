package io.github.tempjr.fintrack.controller;

import io.github.tempjr.fintrack.dto.request.CategorizarTransacaoRequest;
import io.github.tempjr.fintrack.dto.request.EnriquecerTransacaoRequest;
import io.github.tempjr.fintrack.dto.response.PaginaResponse;
import io.github.tempjr.fintrack.dto.response.TransacaoResponse;
import io.github.tempjr.fintrack.model.enums.TipoTransacao;
import io.github.tempjr.fintrack.repository.TransacaoFiltro;
import io.github.tempjr.fintrack.service.TransacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Transações: consulta (RF12) e enriquecimento (RF13, RF14, RF18). O dado bruto é somente leitura. */
@Tag(name = "Transações", description = "Consulta paginada com filtros e enriquecimento (RF12–RF14, RF18)")
@RestController
@RequestMapping("/api/v1/transacoes")
public class TransacaoController {

    /** Mais recentes primeiro; id desempata transações do mesmo dia (paginação estável). */
    private static final Sort ORDEM = Sort.by(Sort.Order.desc("dataPostada"), Sort.Order.desc("id"));

    private final TransacaoService transacaoService;

    public TransacaoController(TransacaoService transacaoService) {
        this.transacaoService = transacaoService;
    }

    @Operation(summary = "Lista transações com filtros combináveis",
            description = "Paginado (RNF03), mais recentes primeiro. Todos os filtros são opcionais.")
    @GetMapping
    public PaginaResponse<TransacaoResponse> listar(
            @RequestParam(required = false) Long contaId,
            @Parameter(description = "Inclui as subcategorias")
            @RequestParam(required = false) Long categoriaId,
            @Parameter(description = "true = só \"Não categorizado\"")
            @RequestParam(defaultValue = "false") boolean semCategoria,
            @RequestParam(required = false) TipoTransacao tipo,
            @Parameter(description = "yyyy-MM-dd, inclusive")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @Parameter(description = "yyyy-MM-dd, inclusive")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @Parameter(description = "Trecho da descrição, sem diferenciar maiúsculas")
            @RequestParam(required = false) String texto,
            @Parameter(description = "Começa em 0")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "não pode ser negativa") int pagina,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "deve ser pelo menos 1") @Max(value = 100, message = "deve ser no máximo 100") int tamanho) {
        TransacaoFiltro filtro = new TransacaoFiltro(contaId, categoriaId, semCategoria, tipo, dataInicio, dataFim, texto);
        return PaginaResponse.from(
                transacaoService.listar(filtro, PageRequest.of(pagina, tamanho, ORDEM)),
                TransacaoResponse::from);
    }

    @Operation(summary = "Detalhe de uma transação")
    @GetMapping("/{id}")
    public TransacaoResponse buscar(@PathVariable Long id) {
        return TransacaoResponse.from(transacaoService.buscar(id));
    }

    @Operation(summary = "Define a categoria manualmente",
            description = "Origem passa a ser MANUAL: as regras não alteram mais esta transação (RF18).")
    @PutMapping("/{id}/categoria")
    public TransacaoResponse categorizar(@PathVariable Long id, @Valid @RequestBody CategorizarTransacaoRequest request) {
        return TransacaoResponse.from(transacaoService.categorizarManualmente(id, request.categoriaId()));
    }

    @Operation(summary = "Remove a categoria", description = "Volta a \"Não categorizado\" (RF20).")
    @DeleteMapping("/{id}/categoria")
    public TransacaoResponse removerCategoria(@PathVariable Long id) {
        return TransacaoResponse.from(transacaoService.removerCategoria(id));
    }

    @Operation(summary = "Atualiza nota, tags e transferência interna",
            description = "Campo ausente não muda. Data, valor e descrição são do banco e não podem ser alterados.")
    @PatchMapping("/{id}")
    public TransacaoResponse enriquecer(@PathVariable Long id, @Valid @RequestBody EnriquecerTransacaoRequest request) {
        return TransacaoResponse.from(transacaoService.enriquecer(
                id, request.nota(), request.tags(), request.transferenciaInterna()));
    }
}
