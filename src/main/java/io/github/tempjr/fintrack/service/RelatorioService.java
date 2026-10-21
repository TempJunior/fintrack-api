package io.github.tempjr.fintrack.service;

import io.github.tempjr.fintrack.repository.FiltroRelatorio;
import io.github.tempjr.fintrack.service.relatorio.PanoramaSaldos;
import io.github.tempjr.fintrack.service.relatorio.ResumoFinanceiro;
import io.github.tempjr.fintrack.service.relatorio.ResumoMes;
import io.github.tempjr.fintrack.service.relatorio.TotalPorCategoria;

import java.time.YearMonth;
import java.util.List;

/** Relatórios (RF21–RF26). Somente leitura; transferências internas ficam fora das somas (RF14). */
public interface RelatorioService {

    int MAXIMO_MESES_EVOLUCAO = 36;
    int MAXIMO_MESES_COMPARATIVO = 12;

    /** Receitas, despesas e saldo do recorte (RF21). */
    ResumoFinanceiro resumo(FiltroRelatorio filtro);

    /** Por categoria raiz, com subcategorias dentro, mais despesas primeiro; "Não categorizado" por último (RF22). */
    List<TotalPorCategoria> porCategoria(FiltroRelatorio filtro);

    /** Um item por mês de {@code inicio} a {@code fim}, inclusive meses sem movimento (RF23). */
    List<ResumoMes> evolucaoMensal(YearMonth inicio, YearMonth fim, Long contaId, Long categoriaId);

    /** Os meses pedidos lado a lado, com totais e categorias (RF24). */
    List<ResumoMes> comparativo(List<YearMonth> meses, Long contaId, Long categoriaId);

    /** Saldo de cada conta ativa e o consolidado (RF25). */
    PanoramaSaldos saldos();
}
