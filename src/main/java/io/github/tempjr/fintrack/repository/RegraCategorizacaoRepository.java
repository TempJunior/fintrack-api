package io.github.tempjr.fintrack.repository;

import io.github.tempjr.fintrack.model.RegraCategorizacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RegraCategorizacaoRepository extends JpaRepository<RegraCategorizacao, Long> {

    /**
     * Regras com a categoria já carregada (JOIN FETCH), em ordem de avaliação.
     * Sem o fetch, ler regra.getCategoria() de cada regra dispararia uma consulta por regra (N+1).
     */
    @Query("select r from RegraCategorizacao r join fetch r.categoria order by r.prioridade, r.id")
    List<RegraCategorizacao> findAllComCategoria();
}
