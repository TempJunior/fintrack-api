package io.github.tempjr.fintrack.repository;

import io.github.tempjr.fintrack.model.Importacao;
import io.github.tempjr.fintrack.model.enums.ResultadoImportacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ImportacaoRepository extends JpaRepository<Importacao, Long> {

    /** Histórico da conta, mais recente primeiro (RF08, RNF12). */
    List<Importacao> findByContaIdOrderByDataHoraDescIdDesc(Long contaId);

    Optional<Importacao> findByIdAndContaId(Long id, Long contaId);

    /**
     * Importações com saldo declarado (D-10), da mais recente para a mais antiga em cada conta.
     * "Mais recente" é pela data do saldo, não da importação: importar um extrato antigo depois de um novo
     * não pode fazer o saldo voltar no tempo.
     */
    @Query("""
            select i from Importacao i
            where i.resultado = :resultado and i.saldoDeclarado is not null
            order by i.conta.id, i.dataSaldo desc, i.id desc
            """)
    List<Importacao> findComSaldo(@Param("resultado") ResultadoImportacao resultado);
}
