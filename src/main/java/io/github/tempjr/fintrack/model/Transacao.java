package io.github.tempjr.fintrack.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "transacoes")
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
public class Transacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "conta_id", nullable = false)
    private Conta conta;
    private LocalDate data;
    private BigDecimal valor;
    private String descricao;

    @ManyToOne
    @Enumerated(value = EnumType.STRING)
    private Categoria categoria;

    private boolean transferenciaInterna = false;
}
