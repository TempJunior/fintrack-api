package io.github.tempjr.fintrack.repository;

import io.github.tempjr.fintrack.model.Conta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContaRepository extends JpaRepository<Conta, Long> {

    List<Conta> findAllByOrderByApelidoAsc();

    List<Conta> findByAtivaOrderByApelidoAsc(boolean ativa);
}
