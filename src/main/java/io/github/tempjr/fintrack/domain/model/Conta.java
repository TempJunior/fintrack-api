package io.github.tempjr.fintrack.domain.model;

import io.github.tempjr.fintrack.domain.enuns.Instituicao;
import io.github.tempjr.fintrack.domain.enuns.TipoConta;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Set;

@Entity
@Table(name = "contas")
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class Conta {

    @Id
    private Long id; // Setado pelo ID real do banco: Exemplo itau = BANKID = 0341

    private String apelido;

    @Enumerated(EnumType.STRING)
    private Instituicao instituicao;

    @Enumerated(EnumType.STRING)
    private TipoConta tipo;

    private boolean ativa;

    @OneToMany(mappedBy = "conta", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Transacao> transacoes;
}
