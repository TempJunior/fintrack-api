package io.github.tempjr.fintrack.model;

import io.github.tempjr.fintrack.model.enums.ResultadoImportacao;
import io.github.tempjr.fintrack.model.enums.StatementFormat;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Registro de auditoria de uma importação (RF08, RNF12). É gravado também quando a importação falha.
 * Criado pelas fábricas {@link #sucesso} e {@link #falha}; não muda depois de gravado.
 */
@Entity
@Table(name = "importacoes")
public class Importacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conta_id", nullable = false, updatable = false)
    private Conta conta;

    @Column(name = "nome_arquivo", nullable = false, updatable = false)
    private String nomeArquivo;

    /** Nulo quando o formato não foi reconhecido. */
    @Enumerated(EnumType.STRING)
    @Column(updatable = false, length = 10)
    private StatementFormat formato;

    @Column(name = "data_hora", nullable = false, updatable = false)
    private Instant dataHora;

    @Column(name = "total_lido", nullable = false, updatable = false)
    private int totalLido;

    @Column(nullable = false, updatable = false)
    private int novas;

    @Column(nullable = false, updatable = false)
    private int duplicadas;

    @Column(nullable = false, updatable = false)
    private int erros;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private ResultadoImportacao resultado;

    /** LEDGERBAL do arquivo: é o saldo da conta nessa data (D-10). */
    @Column(name = "saldo_declarado", updatable = false, precision = 15, scale = 2)
    private BigDecimal saldoDeclarado;

    @Column(name = "data_saldo", updatable = false)
    private LocalDate dataSaldo;

    protected Importacao() {
    }

    private Importacao(Conta conta, String nomeArquivo, StatementFormat formato, ResultadoImportacao resultado) {
        this.conta = Objects.requireNonNull(conta, "conta é obrigatória");
        this.nomeArquivo = Objects.requireNonNull(nomeArquivo, "nomeArquivo é obrigatório");
        this.formato = formato;
        this.resultado = resultado;
        this.dataHora = Instant.now();
    }

    public static Importacao sucesso(Conta conta, String nomeArquivo, StatementFormat formato,
                                     int totalLido, int novas, int duplicadas,
                                     BigDecimal saldoDeclarado, LocalDate dataSaldo) {
        Importacao importacao = new Importacao(conta, nomeArquivo,
                Objects.requireNonNull(formato, "formato é obrigatório"), ResultadoImportacao.SUCESSO);
        importacao.totalLido = totalLido;
        importacao.novas = novas;
        importacao.duplicadas = duplicadas;
        importacao.saldoDeclarado = saldoDeclarado;
        importacao.dataSaldo = dataSaldo;
        return importacao;
    }

    public static Importacao falha(Conta conta, String nomeArquivo, StatementFormat formato, int erros) {
        Importacao importacao = new Importacao(conta, nomeArquivo, formato, ResultadoImportacao.FALHA);
        importacao.erros = erros;
        return importacao;
    }

    public Long getId() {
        return id;
    }

    public Conta getConta() {
        return conta;
    }

    public String getNomeArquivo() {
        return nomeArquivo;
    }

    public StatementFormat getFormato() {
        return formato;
    }

    public Instant getDataHora() {
        return dataHora;
    }

    public int getTotalLido() {
        return totalLido;
    }

    public int getNovas() {
        return novas;
    }

    public int getDuplicadas() {
        return duplicadas;
    }

    public int getErros() {
        return erros;
    }

    public ResultadoImportacao getResultado() {
        return resultado;
    }

    public BigDecimal getSaldoDeclarado() {
        return saldoDeclarado;
    }

    public LocalDate getDataSaldo() {
        return dataSaldo;
    }
}
