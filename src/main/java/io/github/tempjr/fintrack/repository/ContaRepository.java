package io.github.tempjr.fintrack.repository;

import io.github.tempjr.fintrack.domain.enuns.Instituicao;
import io.github.tempjr.fintrack.domain.model.Conta;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContaRepository extends JpaRepository<Conta, Long> {

    boolean existsByApelidoOrInstituicao(String apelido, Instituicao instituicao);
}
