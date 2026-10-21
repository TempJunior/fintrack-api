package io.github.tempjr.fintrack.repository;

import io.github.tempjr.fintrack.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    /** Todas de uma vez (são poucas); a árvore é montada na memória, sem N+1. */
    List<Categoria> findAllByOrderByNomeAsc();

    /** Nome repetido no mesmo nível, sem diferenciar maiúsculas. paiId nulo = categorias raiz. */
    @Query("""
            select count(c) > 0 from Categoria c
            where lower(c.nome) = lower(:nome)
              and ((:paiId is null and c.pai is null) or c.pai.id = :paiId)
              and (:ignorarId is null or c.id <> :ignorarId)
            """)
    boolean existeNomeNoNivel(@Param("nome") String nome, @Param("paiId") Long paiId,
                              @Param("ignorarId") Long ignorarId);
}
