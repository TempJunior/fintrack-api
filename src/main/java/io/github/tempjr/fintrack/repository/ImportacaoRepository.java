package io.github.tempjr.fintrack.repository;

import io.github.tempjr.fintrack.model.Importacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImportacaoRepository extends JpaRepository<Importacao, Long> {

    /** Histórico da conta, mais recente primeiro (RF08, RNF12). */
    List<Importacao> findByContaIdOrderByDataHoraDescIdDesc(Long contaId);

    Optional<Importacao> findByIdAndContaId(Long id, Long contaId);
}
