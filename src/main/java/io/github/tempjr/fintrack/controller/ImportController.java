package io.github.tempjr.fintrack.controller;

import io.github.tempjr.fintrack.dto.response.ImportacaoResponse;
import io.github.tempjr.fintrack.model.Importacao;
import io.github.tempjr.fintrack.service.ImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/** Importação de extratos OFX de uma conta (RF04–RF09) e histórico (RF08, RNF12). */
@Tag(name = "Importações", description = "Upload de extrato OFX e histórico de importações")
@RestController
@RequestMapping("/api/v1/contas/{contaId}/importacoes")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @Operation(summary = "Importa um extrato OFX para a conta",
            description = """
                    Tudo ou nada: se o arquivo for rejeitado, nada é gravado (422) e a tentativa aparece no \
                    histórico como FALHA. Reimportar o mesmo arquivo não duplica transações. Tamanho máximo: 5 MB.""")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportacaoResponse> importar(
            @PathVariable Long contaId,
            @Parameter(description = "Arquivo OFX (v1 ou v2) exportado pelo banco")
            @RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        // O controller só tira os bytes do HTTP; o service não conhece MultipartFile.
        Importacao importacao = importService.importar(contaId, arquivo.getOriginalFilename(), arquivo.getBytes());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(importacao.getId())
                .toUri();
        return ResponseEntity.created(location).body(ImportacaoResponse.from(importacao));
    }

    @Operation(summary = "Histórico de importações da conta", description = "Mais recente primeiro; inclui as falhas.")
    @GetMapping
    public List<ImportacaoResponse> listar(@PathVariable Long contaId) {
        return importService.listar(contaId).stream()
                .map(ImportacaoResponse::from)
                .toList();
    }

    @Operation(summary = "Busca uma importação da conta")
    @GetMapping("/{importacaoId}")
    public ImportacaoResponse buscar(@PathVariable Long contaId, @PathVariable Long importacaoId) {
        return ImportacaoResponse.from(importService.buscar(contaId, importacaoId));
    }
}
