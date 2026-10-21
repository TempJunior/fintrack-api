package io.github.tempjr.fintrack.repository;

import io.github.tempjr.fintrack.model.Conta;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContaRepository extends JpaRepository<Conta, Long> {
}
