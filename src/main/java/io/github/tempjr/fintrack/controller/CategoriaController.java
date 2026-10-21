package io.github.tempjr.fintrack.controller;

import io.github.tempjr.fintrack.dto.request.CriarCategoriaRequest;
import io.github.tempjr.fintrack.dto.request.RenomearCategoriaRequest;
import io.github.tempjr.fintrack.dto.response.CategoriaResponse;
import io.github.tempjr.fintrack.model.Categoria;
import io.github.tempjr.fintrack.service.CategorizacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/** Categorias e subcategorias (RF15). Máximo de 2 níveis. */
@Tag(name = "Categorias", description = "Categorias e subcategorias (RF15)")
@RestController
@RequestMapping("/api/v1/categorias")
public class CategoriaController {

    private final CategorizacaoService categorizacaoService;

    public CategoriaController(CategorizacaoService categorizacaoService) {
        this.categorizacaoService = categorizacaoService;
    }

    @Operation(summary = "Cria uma categoria ou subcategoria",
            description = "Com categoriaPaiId cria subcategoria. Nome repetido no mesmo nível: 409.")
    @PostMapping
    public ResponseEntity<CategoriaResponse> criar(@Valid @RequestBody CriarCategoriaRequest request) {
        Categoria categoria = categorizacaoService.criarCategoria(request.nome(), request.categoriaPaiId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(categoria.getId())
                .toUri();
        return ResponseEntity.created(location).body(CategoriaResponse.from(categoria));
    }

    @Operation(summary = "Lista as categorias em árvore", description = "Raízes ordenadas por nome, com as subcategorias.")
    @GetMapping
    public List<CategoriaResponse> listar() {
        return CategoriaResponse.arvore(categorizacaoService.listarCategorias());
    }

    @Operation(summary = "Busca uma categoria")
    @GetMapping("/{id}")
    public CategoriaResponse buscar(@PathVariable Long id) {
        return CategoriaResponse.from(categorizacaoService.buscarCategoria(id));
    }

    @Operation(summary = "Renomeia uma categoria")
    @PatchMapping("/{id}")
    public CategoriaResponse renomear(@PathVariable Long id, @Valid @RequestBody RenomearCategoriaRequest request) {
        return CategoriaResponse.from(categorizacaoService.renomearCategoria(id, request.nome()));
    }
}
