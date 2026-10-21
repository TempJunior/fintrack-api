package io.github.tempjr.fintrack.repository;

import io.github.tempjr.fintrack.model.Transacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

public interface TransacaoRepository extends JpaRepository<Transacao, Long> {

    /**
     * Das chaves informadas, devolve as que já existem na conta. Uma consulta só para o arquivo inteiro,
     * em vez de uma por transação. A constraint UNIQUE (conta_id, dedup_key) continua sendo a rede de segurança.
     */
    @Query("select t.dedupKey from Transacao t where t.conta.id = :contaId and t.dedupKey in :chaves")
    Set<String> findDedupKeysExistentes(@Param("contaId") Long contaId, @Param("chaves") Collection<String> chaves);

    /** Sempre paginado (RNF03). */
    Page<Transacao> findByContaId(Long contaId, Pageable pageable);
}
