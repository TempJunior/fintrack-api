package io.github.tempjr.fintrack.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Categoria ou subcategoria (RF15). Subcategoria = tem pai. */
@Entity
@Table(name = "categorias")
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String nome;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_pai_id")
    private Categoria pai;

    protected Categoria() {
    }

    public Categoria(String nome) {
        this(nome, null);
    }

    /** @param pai categoria raiz; subcategoria de subcategoria não é permitida (2 níveis) */
    public Categoria(String nome, Categoria pai) {
        if (pai != null && pai.isSubcategoria()) {
            throw new IllegalArgumentException("subcategoria não pode ter subcategorias (máximo de 2 níveis)");
        }
        this.nome = validarNome(nome);
        this.pai = pai;
    }

    public void renomear(String novoNome) {
        this.nome = validarNome(novoNome);
    }

    private static String validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("nome é obrigatório");
        }
        return nome.strip();
    }

    public boolean isSubcategoria() {
        return pai != null;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public Categoria getPai() {
        return pai;
    }

    @Override
    public String toString() {
        return "Categoria{id=" + id + ", nome='" + nome + "'}";
    }
}
