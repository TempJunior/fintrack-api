package io.github.tempjr.fintrack.repository;

import io.github.tempjr.fintrack.model.Categoria;
import io.github.tempjr.fintrack.model.Transacao;
import io.github.tempjr.fintrack.model.enums.TipoTransacao;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Monta o WHERE da listagem a partir do {@link TransacaoFiltro} (RF12).
 * Cada filtro é uma Specification pequena; só as dos campos preenchidos entram, ligadas por AND.
 * É a alternativa a escrever uma @Query para cada combinação de filtros.
 */
public final class TransacaoSpecs {

    private static final char ESCAPE = '\\';

    private TransacaoSpecs() {
    }

    public static Specification<Transacao> com(TransacaoFiltro filtro) {
        List<Specification<Transacao>> partes = new ArrayList<>();
        if (filtro.contaId() != null) {
            partes.add(daConta(filtro.contaId()));
        }
        if (filtro.categoriaId() != null) {
            partes.add(daCategoriaOuSubcategoria(filtro.categoriaId()));
        }
        if (filtro.semCategoria()) {
            partes.add(semCategoria());
        }
        if (filtro.tipo() != null) {
            partes.add(doTipo(filtro.tipo()));
        }
        if (filtro.dataInicio() != null) {
            partes.add(aPartirDe(filtro.dataInicio()));
        }
        if (filtro.dataFim() != null) {
            partes.add(ate(filtro.dataFim()));
        }
        if (filtro.texto() != null) {
            partes.add(descricaoContem(filtro.texto()));
        }
        return partes.stream()
                .reduce(Specification::and)
                .orElse((root, query, cb) -> cb.conjunction()); // sem filtro: tudo
    }

    static Specification<Transacao> daConta(Long contaId) {
        return (root, query, cb) -> cb.equal(root.get("conta").get("id"), contaId);
    }

    /** Filtrar por "Alimentação" traz também "Alimentação → Restaurante". */
    static Specification<Transacao> daCategoriaOuSubcategoria(Long categoriaId) {
        return (root, query, cb) -> {
            Join<Transacao, Categoria> categoria = root.join("categoria", JoinType.LEFT);
            return cb.or(
                    cb.equal(categoria.get("id"), categoriaId),
                    cb.equal(categoria.get("pai").get("id"), categoriaId));
        };
    }

    static Specification<Transacao> semCategoria() {
        return (root, query, cb) -> cb.isNull(root.get("categoria"));
    }

    /** O tipo não é coluna: vem do sinal do valor (zero conta como crédito, igual a Transacao.getTipo). */
    static Specification<Transacao> doTipo(TipoTransacao tipo) {
        return (root, query, cb) -> tipo == TipoTransacao.DEBITO
                ? cb.lessThan(root.get("valor"), BigDecimal.ZERO)
                : cb.greaterThanOrEqualTo(root.get("valor"), BigDecimal.ZERO);
    }

    static Specification<Transacao> aPartirDe(LocalDate data) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dataPostada"), data);
    }

    static Specification<Transacao> ate(LocalDate data) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("dataPostada"), data);
    }

    /** LIKE sem diferenciar maiúsculas. % e _ digitados pelo usuário são tratados como texto, não curinga. */
    static Specification<Transacao> descricaoContem(String texto) {
        String padrao = "%" + escaparLike(texto.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("descricaoOriginal")), padrao, ESCAPE);
    }

    static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
