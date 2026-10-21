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
import jakarta.persistence.Transient;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Regra que atribui uma categoria pela descrição da transação (RF16).
 * Menor prioridade é avaliada primeiro.
 * <p>
 * CONTAINS e STARTS_WITH ignoram maiúsculas e acentos: "padaria sao" casa com "PADARIA SÃO JOÃO".
 * REGEX roda sobre a descrição original, ignorando maiúsculas.
 */
@Entity
@Table(name = "regras_categorizacao")
public class RegraCategorizacao {

    private static final Pattern MARCAS_DE_ACENTO = Pattern.compile("\\p{M}+");

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

    /** Cache: compilar a regex a cada transação seria desperdício. Não vai para o banco. */
    @Transient
    private Pattern regexCompilada;

    protected RegraCategorizacao() {
    }

    public RegraCategorizacao(Categoria categoria, Operador operador, String padrao, int prioridade) {
        this.categoria = Objects.requireNonNull(categoria, "categoria é obrigatória");
        this.operador = Objects.requireNonNull(operador, "operador é obrigatório");
        if (padrao == null || padrao.isBlank()) {
            throw new IllegalArgumentException("padrão é obrigatório");
        }
        if (prioridade < 0) {
            throw new IllegalArgumentException("prioridade não pode ser negativa");
        }
        this.padrao = padrao.strip();
        this.prioridade = prioridade;
        if (operador == Operador.REGEX) {
            regex(); // valida já na criação: erro de sintaxe aparece agora, não na importação
        }
    }

    public boolean corresponde(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            return false;
        }
        return switch (operador) {
            case CONTAINS -> normalizar(descricao).contains(normalizar(padrao));
            case STARTS_WITH -> normalizar(descricao).startsWith(normalizar(padrao));
            case REGEX -> regex().matcher(descricao).find();
        };
    }

    private Pattern regex() {
        if (regexCompilada == null) {
            try {
                regexCompilada = Pattern.compile(padrao, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("padrão não é uma expressão regular válida", e);
            }
        }
        return regexCompilada;
    }

    /** "  Padaria   São João " → "PADARIA SAO JOAO". */
    static String normalizar(String texto) {
        String semAcento = MARCAS_DE_ACENTO.matcher(Normalizer.normalize(texto, Normalizer.Form.NFD)).replaceAll("");
        return semAcento.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
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
