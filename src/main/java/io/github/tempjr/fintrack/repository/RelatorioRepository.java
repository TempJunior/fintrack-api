package io.github.tempjr.fintrack.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Consultas de agregação dos relatórios (RF21–RF26). Somente leitura.
 * <p>
 * As somas acontecem no banco (SUM / GROUP BY): volta uma linha por grupo, não as transações.
 * Regras comuns a todas as consultas:
 * <ul>
 *   <li>Transferência interna fica de fora (RF14).</li>
 *   <li>Receita = valor positivo; despesa = valor negativo, devolvida como número positivo.</li>
 * </ul>
 * O JPQL é montado só com os filtros preenchidos, com parâmetros nomeados (nunca concatenando valores).
 */
@Repository
public class RelatorioRepository {

    /** SUM de CASE sem ELSE: linhas que não entram somam nada (null), em vez de misturar 0 inteiro com decimal. */
    private static final String RECEITAS = "sum(case when t.valor > 0 then t.valor end)";
    private static final String DESPESAS = "sum(case when t.valor < 0 then t.valor end)";

    private final EntityManager em;

    public RelatorioRepository(EntityManager em) {
        this.em = em;
    }

    public record Totais(BigDecimal receitas, BigDecimal despesas) {
    }

    /** @param categoriaId nulo = "Não categorizado" */
    public record TotalCategoria(Long categoriaId, BigDecimal receitas, BigDecimal despesas, long quantidade) {
    }

    public record TotalMes(int ano, int mes, BigDecimal receitas, BigDecimal despesas) {
    }

    public Totais totais(FiltroRelatorio filtro) {
        Consulta consulta = new Consulta("select " + RECEITAS + ", " + DESPESAS, filtro);
        Object[] linha = consulta.executar(Object[].class).get(0); // agregação sem GROUP BY: sempre 1 linha
        return new Totais(decimal(linha[0]), despesa(linha[1]));
    }

    public List<TotalCategoria> porCategoria(FiltroRelatorio filtro) {
        Consulta consulta = new Consulta("select c.id, " + RECEITAS + ", " + DESPESAS + ", count(t)", filtro)
                .agruparPor("c.id");
        return consulta.executar(Object[].class).stream()
                .map(l -> new TotalCategoria((Long) l[0], decimal(l[1]), despesa(l[2]), ((Number) l[3]).longValue()))
                .toList();
    }

    public List<TotalMes> porMes(FiltroRelatorio filtro) {
        String ano = "extract(year from t.dataPostada)";
        String mes = "extract(month from t.dataPostada)";
        Consulta consulta = new Consulta("select " + ano + ", " + mes + ", " + RECEITAS + ", " + DESPESAS, filtro)
                .agruparPor(ano + ", " + mes);
        return consulta.executar(Object[].class).stream()
                .map(l -> new TotalMes(((Number) l[0]).intValue(), ((Number) l[1]).intValue(),
                        decimal(l[2]), despesa(l[3])))
                .toList();
    }

    /** Monta "select ... from ... where ... group by ..." com só os filtros preenchidos. */
    private final class Consulta {

        private final StringBuilder jpql = new StringBuilder();
        private final Map<String, Object> parametros = new HashMap<>();

        Consulta(String select, FiltroRelatorio filtro) {
            // LEFT JOIN: transações sem categoria também entram ("Não categorizado").
            jpql.append(select).append(" from Transacao t left join t.categoria c where t.transferenciaInterna = false");
            if (filtro.contaId() != null) {
                jpql.append(" and t.conta.id = :contaId");
                parametros.put("contaId", filtro.contaId());
            }
            if (filtro.categoriaId() != null) {
                jpql.append(" and (c.id = :categoriaId or c.pai.id = :categoriaId)");
                parametros.put("categoriaId", filtro.categoriaId());
            }
            if (filtro.dataInicio() != null) {
                jpql.append(" and t.dataPostada >= :dataInicio");
                parametros.put("dataInicio", filtro.dataInicio());
            }
            if (filtro.dataFim() != null) {
                jpql.append(" and t.dataPostada <= :dataFim");
                parametros.put("dataFim", filtro.dataFim());
            }
        }

        Consulta agruparPor(String expressao) {
            jpql.append(" group by ").append(expressao);
            return this;
        }

        <T> List<T> executar(Class<T> tipo) {
            TypedQuery<T> query = em.createQuery(jpql.toString(), tipo);
            parametros.forEach(query::setParameter);
            return query.getResultList();
        }
    }

    private static BigDecimal decimal(Object valor) {
        if (valor == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal bd = valor instanceof BigDecimal b ? b : new BigDecimal(valor.toString());
        return bd.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** A soma das despesas vem negativa do banco; o relatório mostra o valor positivo. */
    private static BigDecimal despesa(Object valor) {
        return decimal(valor).abs();
    }
}
