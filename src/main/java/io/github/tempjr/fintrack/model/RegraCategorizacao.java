package io.github.tempjr.fintrack.model;

import io.github.tempjr.fintrack.model.enums.Operador;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Regra que atribui uma categoria pela descrição da transação (RF16).
 * Menor prioridade é avaliada primeiro. A lógica de comparação entra na Etapa 5.
 */
@Entity
@Table(name = "regras_categorizacao")
public class RegraCategorizacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Operador operador;

    @Column(nullable = false)
    private String padrao;

    @Column(nullable = false)
    private int prioridade;

    protected RegraCategorizacao() {
    }

    public RegraCategorizacao(Categoria categoria, Operador operador, String padrao, int prioridade) {
        this.categoria = Objects.requireNonNull(categoria, "categoria é obrigatória");
        this.operador = Objects.requireNonNull(operador, "operador é obrigatório");
        if (padrao == null || padrao.isBlank()) {
            throw new IllegalArgumentException("padrao é obrigatório");
        }
        this.padrao = padrao;
        this.prioridade = prioridade;
    }

    public Long getId() {
        return id;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public Operador getOperador() {
        return operador;
    }

    public String getPadrao() {
        return padrao;
    }

    public int getPrioridade() {
        return prioridade;
    }
}
