package io.github.tempjr.fintrack.model;

import io.github.tempjr.fintrack.model.enums.OrigemCategoria;
import io.github.tempjr.fintrack.model.enums.TipoTransacao;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Transação importada de um extrato.
 * <p>
 * Campos brutos (conta, fitId, dedupKey, dataPostada, valor, descricaoOriginal) são imutáveis
 * após a importação (RF11, RNF06): entram pelo construtor, não têm setter e usam updatable = false.
 * O resto é enriquecimento e muda pelos métodos de negócio.
 */
@Entity
@Table(name = "transacoes")
public class Transacao {

    static final int TAMANHO_MAXIMO_TAG = 40;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---- dado bruto (imutável) ----

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conta_id", nullable = false, updatable = false)
    private Conta conta;

    @Column(name = "fit_id", updatable = false)
    private String fitId;

    @Column(name = "dedup_key", nullable = false, updatable = false)
    private String dedupKey;

    @Column(name = "data_postada", nullable = false, updatable = false)
    private LocalDate dataPostada;

    @Column(nullable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(name = "descricao_original", nullable = false, updatable = false)
    private String descricaoOriginal;

    // ---- enriquecimento (mutável) ----

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem_categoria", length = 10)
    private OrigemCategoria origemCategoria;

    @Column(length = 500)
    private String nota;

    @Column(name = "transferencia_interna", nullable = false)
    private boolean transferenciaInterna = false;

    @ElementCollection
    @CollectionTable(name = "transacao_tags", joinColumns = @JoinColumn(name = "transacao_id"))
    @Column(name = "tag", nullable = false, length = TAMANHO_MAXIMO_TAG)
    private Set<String> tags = new HashSet<>();

    protected Transacao() {
    }

    /**
     * @param fitId FITID do OFX; pode ser nulo (aí a dedup usa o hash — ADR-005)
     * @param valor negativo = débito; no máximo 2 casas decimais
     */
    public Transacao(Conta conta, String fitId, LocalDate dataPostada, BigDecimal valor, String descricaoOriginal) {
        this.conta = Objects.requireNonNull(conta, "conta é obrigatória");
        this.dataPostada = Objects.requireNonNull(dataPostada, "dataPostada é obrigatória");
        this.valor = normalizarValor(valor);
        this.descricaoOriginal = Objects.requireNonNull(descricaoOriginal, "descricaoOriginal é obrigatória");
        this.fitId = (fitId == null || fitId.isBlank()) ? null : fitId.strip();
        this.dedupKey = DedupKey.gerar(this.fitId, this.dataPostada, this.valor, this.descricaoOriginal);
    }

    /**
     * Garante escala 2 sem arredondar: 10.5 vira 10.50, mas 10.555 é rejeitado.
     * Arredondar em silêncio esconderia um valor errado vindo do arquivo.
     */
    private static BigDecimal normalizarValor(BigDecimal valor) {
        Objects.requireNonNull(valor, "valor é obrigatório");
        try {
            return valor.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("valor com mais de 2 casas decimais", e);
        }
    }

    /** Negativo = débito (arquitetura §4.2). Zero conta como crédito. */
    public TipoTransacao getTipo() {
        return valor.signum() < 0 ? TipoTransacao.DEBITO : TipoTransacao.CREDITO;
    }

    /** Escolha do usuário. Tem prioridade sobre qualquer regra (RF18). */
    public void categorizarManualmente(Categoria categoria) {
        this.categoria = Objects.requireNonNull(categoria, "categoria é obrigatória");
        this.origemCategoria = OrigemCategoria.MANUAL;
    }

    /**
     * Aplica a categoria vinda de uma regra, a menos que o usuário já tenha escolhido à mão (RF18, D-11).
     *
     * @return true se a categoria foi aplicada
     */
    public boolean categorizarPorRegra(Categoria categoria) {
        Objects.requireNonNull(categoria, "categoria é obrigatória");
        if (origemCategoria == OrigemCategoria.MANUAL) {
            return false;
        }
        this.categoria = categoria;
        this.origemCategoria = OrigemCategoria.REGRA;
        return true;
    }

    /** Volta para "Não categorizado" (RF20). */
    public void removerCategoria() {
        this.categoria = null;
        this.origemCategoria = null;
    }

    public void marcarTransferenciaInterna(boolean transferenciaInterna) {
        this.transferenciaInterna = transferenciaInterna;
    }

    public void anotar(String nota) {
        this.nota = (nota == null || nota.isBlank()) ? null : nota.strip();
    }

    public void adicionarTag(String tag) {
        tags.add(normalizarTag(tag));
    }

    public void removerTag(String tag) {
        if (tag != null) {
            tags.remove(tag.strip().toLowerCase(Locale.ROOT));
        }
    }

    /** Troca todas as tags de uma vez (PATCH com a lista nova). Valida tudo antes de mexer. */
    public void substituirTags(Collection<String> novas) {
        Set<String> normalizadas = new HashSet<>();
        for (String tag : novas) {
            normalizadas.add(normalizarTag(tag));
        }
        tags.clear();
        tags.addAll(normalizadas);
    }

    private static String normalizarTag(String tag) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("tag não pode ser vazia");
        }
        String normalizada = tag.strip().toLowerCase(Locale.ROOT);
        if (normalizada.length() > TAMANHO_MAXIMO_TAG) {
            throw new IllegalArgumentException("tag deve ter no máximo " + TAMANHO_MAXIMO_TAG + " caracteres");
        }
        return normalizada;
    }

    public Long getId() {
        return id;
    }

    public Conta getConta() {
        return conta;
    }

    public String getFitId() {
        return fitId;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public LocalDate getDataPostada() {
        return dataPostada;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public String getDescricaoOriginal() {
        return descricaoOriginal;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public OrigemCategoria getOrigemCategoria() {
        return origemCategoria;
    }

    public String getNota() {
        return nota;
    }

    public boolean isTransferenciaInterna() {
        return transferenciaInterna;
    }

    public Set<String> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    /** Sem valor, descrição nem nota de propósito: toString acaba em log (RNF08). */
    @Override
    public String toString() {
        return "Transacao{id=" + id + ", dataPostada=" + dataPostada + ", tipo=" + getTipo() + "}";
    }
}
