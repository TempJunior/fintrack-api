package io.github.tempjr.fintrack.repository;

import io.github.tempjr.fintrack.model.Transacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransacaoRepository extends JpaRepository<Transacao, Long> {

    /** Checagem da dedup na aplicação; a constraint UNIQUE (conta_id, dedup_key) é a rede de segurança. */
    boolean existsByContaIdAndDedupKey(Long contaId, String dedupKey);

    /** Sempre paginado (RNF03). */
    Page<Transacao> findByContaId(Long contaId, Pageable pageable);
}
