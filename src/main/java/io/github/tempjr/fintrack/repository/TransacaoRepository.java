package io.github.tempjr.fintrack.repository;

import io.github.tempjr.fintrack.domain.model.Transacao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransacaoRepository extends JpaRepository<Transacao, Long> {
}
