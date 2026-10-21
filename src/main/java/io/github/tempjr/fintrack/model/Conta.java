package io.github.tempjr.fintrack.model;

import io.github.tempjr.fintrack.model.enums.Instituicao;
import io.github.tempjr.fintrack.model.enums.TipoConta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Conta financeira (RF01). Não é excluída, só inativada (RF02).
 * Não tem lista de transações: a relação é mapeada só do lado de Transacao.
 */
@Entity
@Table(name = "contas")
public class Conta {

    public static final String MOEDA_PADRAO = "BRL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private Instituicao instituicao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private TipoConta tipo;

    @Column(nullable = false, length = 60)
    private String apelido;

    @Column(nullable = false, length = 3)
    private String moeda = MOEDA_PADRAO;

    /** ACCTID do OFX (D-09). Preenchido na 1ª importação. Dado sensível: nunca logar (RNF08). */
    @Column(name = "numero_externo", length = 40)
    private String numeroExterno;

    @Column(nullable = false)
    private boolean ativa = true;

    /** Exigido pelo JPA. Não use no código da aplicação. */
    protected Conta() {
    }

    public Conta(Instituicao instituicao, TipoConta tipo, String apelido) {
        this.instituicao = Objects.requireNonNull(instituicao, "instituicao é obrigatória");
        this.tipo = Objects.requireNonNull(tipo, "tipo é obrigatório");
        this.apelido = validarApelido(apelido);
    }

    public void renomear(String novoApelido) {
        this.apelido = validarApelido(novoApelido);
    }

    public void inativar() {
        this.ativa = false;
    }

    /**
     * Confere o número da conta declarado no arquivo (ACCTID) com o desta conta (D-09).
     * Na primeira importação o número ainda não é conhecido: ele é vinculado à conta.
     * Nas seguintes, um número diferente indica arquivo de outra conta.
     *
     * @return false se o arquivo for de outra conta
     */
    public boolean conferirOuVincularNumeroExterno(String numeroNoArquivo) {
        if (numeroNoArquivo == null || numeroNoArquivo.isBlank()) {
            return true; // arquivo sem ACCTID: nada a conferir
        }
        String numero = numeroNoArquivo.strip();
        if (numeroExterno == null) {
            numeroExterno = numero;
            return true;
        }
        return numeroExterno.equals(numero);
    }

    private static String validarApelido(String apelido) {
        if (apelido == null || apelido.isBlank()) {
            throw new IllegalArgumentException("apelido é obrigatório");
        }
        return apelido.strip();
    }

    public Long getId() {
        return id;
    }

    public Instituicao getInstituicao() {
        return instituicao;
    }

    public TipoConta getTipo() {
        return tipo;
    }

    public String getApelido() {
        return apelido;
    }

    public String getMoeda() {
        return moeda;
    }

    public String getNumeroExterno() {
        return numeroExterno;
    }

    public boolean isAtiva() {
        return ativa;
    }

    /** Sem numeroExterno de propósito (RNF08). */
    @Override
    public String toString() {
        return "Conta{id=" + id + ", instituicao=" + instituicao + ", tipo=" + tipo + ", ativa=" + ativa + "}";
    }
}
