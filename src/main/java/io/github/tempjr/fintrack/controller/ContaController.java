package io.github.tempjr.fintrack.controller;

import io.github.tempjr.fintrack.dto.request.AtualizarContaRequest;
import io.github.tempjr.fintrack.dto.request.CriarContaRequest;
import io.github.tempjr.fintrack.dto.response.ContaResponse;
import io.github.tempjr.fintrack.model.Conta;
import io.github.tempjr.fintrack.service.ContaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/** Contas financeiras (RF01–RF03). Só traduz HTTP ↔ service; regra de negócio fica no service e na entidade. */
@Tag(name = "Contas", description = "Contas financeiras (RF01–RF03)")
@RestController
@RequestMapping("/api/v1/contas")
public class ContaController {

    private final ContaService contaService;

    public ContaController(ContaService contaService) {
        this.contaService = contaService;
    }

    @Operation(summary = "Cadastra uma conta", description = "Moeda sempre BRL. Responde 201 com Location.")
    @PostMapping
    public ResponseEntity<ContaResponse> criar(@Valid @RequestBody CriarContaRequest request) {
        Conta conta = contaService.criar(request.instituicao(), request.tipo(), request.apelido());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(conta.getId())
                .toUri();
        return ResponseEntity.created(location).body(ContaResponse.from(conta));
    }

    @Operation(summary = "Lista as contas", description = "Ordenadas pelo apelido.")
    @GetMapping
    public List<ContaResponse> listar(
            @Parameter(description = "true = só ativas; false = só inativas; ausente = todas")
            @RequestParam(required = false) Boolean ativa) {
        return contaService.listar(ativa).stream()
                .map(ContaResponse::from)
                .toList();
    }

    @Operation(summary = "Busca uma conta pelo id")
    @GetMapping("/{id}")
    public ContaResponse buscar(@PathVariable Long id) {
        return ContaResponse.from(contaService.buscarPorId(id));
    }

    @Operation(summary = "Altera o apelido da conta", description = "Instituição e tipo não podem ser alterados.")
    @PatchMapping("/{id}")
    public ContaResponse atualizar(@PathVariable Long id, @Valid @RequestBody AtualizarContaRequest request) {
        return ContaResponse.from(contaService.renomear(id, request.apelido()));
    }

    @Operation(summary = "Inativa a conta", description = "Contas não são excluídas (RF02). Responde 204.")
    @PostMapping("/{id}/inativar")
    public ResponseEntity<Void> inativar(@PathVariable Long id) {
        contaService.inativar(id);
        return ResponseEntity.noContent().build();
    }
}
