package io.github.tempjr.fintrack.repository;

import io.github.tempjr.fintrack.model.Transacao;
import io.github.tempjr.fintrack.model.enums.OrigemCategoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface TransacaoRepository extends JpaRepository<Transacao, Long>, JpaSpecificationExecutor<Transacao> {

    /**
     * Listagem com filtros (RF12). O @EntityGraph traz conta e categoria no mesmo SELECT (JOIN), evitando
     * uma consulta extra por linha (N+1). As tags (coleção) não entram aqui: JOIN com coleção quebra a
     * paginação no banco. Elas vêm em lote pelo hibernate.default_batch_fetch_size.
     */
    @Override
    @EntityGraph(attributePaths = {"conta", "categoria"})
    Page<Transacao> findAll(Specification<Transacao> spec, Pageable pageable);

    /** Uma transação com tudo carregado, para detalhe e edição. */
    @EntityGraph(attributePaths = {"conta", "categoria", "tags"})
    Optional<Transacao> findDetalhadaById(Long id);


    /**
     * Das chaves informadas, devolve as que já existem na conta. Uma consulta só para o arquivo inteiro,
     * em vez de uma por transação. A constraint UNIQUE (conta_id, dedup_key) continua sendo a rede de segurança.
     */
    @Query("select t.dedupKey from Transacao t where t.conta.id = :contaId and t.dedupKey in :chaves")
    Set<String> findDedupKeysExistentes(@Param("contaId") Long contaId, @Param("chaves") Collection<String> chaves);

    /** Sempre paginado (RNF03). */
    Page<Transacao> findByContaId(Long contaId, Pageable pageable);

    /**
     * Transações que as regras podem mudar: sem categoria ou categorizadas por regra (D-11).
     * Slice em vez de Page: não precisa do total, então não roda um count por página.
     * Ordenado por id para a paginação ser estável enquanto o lote altera as categorias.
     */
    @Query("select t from Transacao t where t.origemCategoria is null or t.origemCategoria = :regra order by t.id")
    Slice<Transacao> findCategorizaveis(@Param("regra") OrigemCategoria regra, Pageable pageable);
}
