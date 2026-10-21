package io.github.tempjr.fintrack.controller;

import io.github.tempjr.fintrack.dto.request.CriarRegraRequest;
import io.github.tempjr.fintrack.dto.response.RegraResponse;
import io.github.tempjr.fintrack.model.RegraCategorizacao;
import io.github.tempjr.fintrack.service.CategorizacaoService;
import io.github.tempjr.fintrack.service.ResultadoReaplicacao;
import io.github.tempjr.fintrack.service.TransacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/** Regras de categorização (RF16) e reaplicação em lote (RF19). */
@Tag(name = "Regras", description = "Regras de categorização automática (RF16–RF19)")
@RestController
@RequestMapping("/api/v1/regras")
public class RegraController {

    private final CategorizacaoService categorizacaoService;
    private final TransacaoService transacaoService;

    public RegraController(CategorizacaoService categorizacaoService, TransacaoService transacaoService) {
        this.categorizacaoService = categorizacaoService;
        this.transacaoService = transacaoService;
    }

    @Operation(summary = "Cria uma regra",
            description = "Aplicada automaticamente nas próximas importações. Para o histórico, use /reaplicar.")
    @PostMapping
    public ResponseEntity<RegraResponse> criar(@Valid @RequestBody CriarRegraRequest request) {
        RegraCategorizacao regra = categorizacaoService.criarRegra(
                request.categoriaId(), request.operador(), request.padrao(), request.prioridade());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(regra.getId())
                .toUri();
        return ResponseEntity.created(location).body(RegraResponse.from(regra));
    }

    @Operation(summary = "Lista as regras", description = "Em ordem de avaliação (prioridade, depois criação).")
    @GetMapping
    public List<RegraResponse> listar() {
        return categorizacaoService.listarRegras().stream()
                .map(RegraResponse::from)
                .toList();
    }

    @Operation(summary = "Exclui uma regra",
            description = "Transações já categorizadas por ela só mudam ao reaplicar as regras.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        categorizacaoService.excluirRegra(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reaplica as regras em todas as transações",
            description = "Não altera categorias escolhidas manualmente (RF18). Transações categorizadas por regra "
                    + "que não casam mais com nenhuma voltam a \"Não categorizado\".")
    @PostMapping("/reaplicar")
    public ResultadoReaplicacao reaplicar() {
        return transacaoService.reaplicarRegras();
    }
}
