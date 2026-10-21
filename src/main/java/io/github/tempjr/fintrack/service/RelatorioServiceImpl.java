package io.github.tempjr.fintrack.service;

import io.github.tempjr.fintrack.model.Categoria;
import io.github.tempjr.fintrack.model.Conta;
import io.github.tempjr.fintrack.model.Importacao;
import io.github.tempjr.fintrack.model.enums.ResultadoImportacao;
import io.github.tempjr.fintrack.repository.FiltroRelatorio;
import io.github.tempjr.fintrack.repository.ImportacaoRepository;
import io.github.tempjr.fintrack.repository.RelatorioRepository;
import io.github.tempjr.fintrack.repository.RelatorioRepository.TotalCategoria;
import io.github.tempjr.fintrack.repository.RelatorioRepository.TotalMes;
import io.github.tempjr.fintrack.service.relatorio.PanoramaSaldos;
import io.github.tempjr.fintrack.service.relatorio.PanoramaSaldos.SaldoConta;
import io.github.tempjr.fintrack.service.relatorio.ResumoFinanceiro;
import io.github.tempjr.fintrack.service.relatorio.ResumoMes;
import io.github.tempjr.fintrack.service.relatorio.TotalPorCategoria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pela regra das camadas, o RelatorioService é o único que lê repositories de outros "donos" direto
 * (somente leitura, para agregações). Ele não altera nada.
 */
@Service
@Transactional(readOnly = true)
public class RelatorioServiceImpl implements RelatorioService {

    static final String NAO_CATEGORIZADO = "Não categorizado";

    private static final Comparator<TotalPorCategoria> MAIS_DESPESA_PRIMEIRO =
            Comparator.comparing((TotalPorCategoria t) -> t.resumo().despesas()).reversed()
                    .thenComparing(TotalPorCategoria::nome);

    private final RelatorioRepository relatorioRepository;
    private final ImportacaoRepository importacaoRepository;
    private final CategorizacaoService categorizacaoService;
    private final ContaService contaService;

    public RelatorioServiceImpl(RelatorioRepository relatorioRepository,
                                ImportacaoRepository importacaoRepository,
                                CategorizacaoService categorizacaoService,
                                ContaService contaService) {
        this.relatorioRepository = relatorioRepository;
        this.importacaoRepository = importacaoRepository;
        this.categorizacaoService = categorizacaoService;
        this.contaService = contaService;
    }

    @Override
    public ResumoFinanceiro resumo(FiltroRelatorio filtro) {
        RelatorioRepository.Totais totais = relatorioRepository.totais(filtro);
        return new ResumoFinanceiro(totais.receitas(), totais.despesas());
    }

    @Override
    public List<TotalPorCategoria> porCategoria(FiltroRelatorio filtro) {
        Map<Long, Categoria> categorias = categorizacaoService.listarCategorias().stream()
                .collect(Collectors.toMap(Categoria::getId, Function.identity()));
        return agruparPorRaiz(relatorioRepository.porCategoria(filtro), categorias);
    }

    /**
     * O banco agrupa por categoria (raiz ou sub). Aqui as subcategorias são somadas na raiz, que é como o
     * usuário pensa ("quanto gastei com Alimentação?"), e continuam detalhadas dentro dela.
     */
    static List<TotalPorCategoria> agruparPorRaiz(List<TotalCategoria> linhas, Map<Long, Categoria> categorias) {
        Map<Long, TotalCategoria> daPropriaRaiz = new HashMap<>();
        Map<Long, List<TotalPorCategoria>> subsPorRaiz = new LinkedHashMap<>();
        TotalPorCategoria naoCategorizado = null;

        for (TotalCategoria linha : linhas) {
            if (linha.categoriaId() == null) {
                naoCategorizado = new TotalPorCategoria(null, NAO_CATEGORIZADO, resumo(linha), linha.quantidade(), List.of());
                continue;
            }
            Categoria categoria = Objects.requireNonNull(categorias.get(linha.categoriaId()));
            if (categoria.isSubcategoria()) {
                Long raizId = categoria.getPai().getId();
                subsPorRaiz.computeIfAbsent(raizId, k -> new ArrayList<>()).add(new TotalPorCategoria(
                        categoria.getId(), categoria.getNome(), resumo(linha), linha.quantidade(), List.of()));
                daPropriaRaiz.putIfAbsent(raizId, null); // garante que a raiz apareça mesmo sem lançamento direto
            } else {
                daPropriaRaiz.put(categoria.getId(), linha);
            }
        }

        List<TotalPorCategoria> raizes = new ArrayList<>();
        for (Long raizId : daPropriaRaiz.keySet()) {
            TotalCategoria propria = daPropriaRaiz.get(raizId);
            List<TotalPorCategoria> subs = subsPorRaiz.getOrDefault(raizId, List.of());
            ResumoFinanceiro total = propria == null ? ResumoFinanceiro.ZERADO : resumo(propria);
            long quantidade = propria == null ? 0 : propria.quantidade();
            for (TotalPorCategoria sub : subs) {
                total = total.somar(sub.resumo());
                quantidade += sub.quantidade();
            }
            raizes.add(new TotalPorCategoria(raizId, categorias.get(raizId).getNome(), total, quantidade,
                    subs.stream().sorted(MAIS_DESPESA_PRIMEIRO).toList()));
        }
        raizes.sort(MAIS_DESPESA_PRIMEIRO);
        if (naoCategorizado != null) {
            raizes.add(naoCategorizado);
        }
        return raizes;
    }

    @Override
    public List<ResumoMes> evolucaoMensal(YearMonth inicio, YearMonth fim, Long contaId, Long categoriaId) {
        List<YearMonth> meses = mesesEntre(inicio, fim);
        FiltroRelatorio filtro = new FiltroRelatorio(contaId, categoriaId, inicio.atDay(1), fim.atEndOfMonth());

        Map<YearMonth, ResumoFinanceiro> comMovimento = new HashMap<>();
        for (TotalMes linha : relatorioRepository.porMes(filtro)) {
            comMovimento.put(YearMonth.of(linha.ano(), linha.mes()),
                    new ResumoFinanceiro(linha.receitas(), linha.despesas()));
        }
        // O banco só devolve meses com transação; o gráfico precisa de todos (mês sem movimento = zero).
        return meses.stream()
                .map(mes -> new ResumoMes(mes, comMovimento.getOrDefault(mes, ResumoFinanceiro.ZERADO), List.of()))
                .toList();
    }

    static List<YearMonth> mesesEntre(YearMonth inicio, YearMonth fim) {
        Objects.requireNonNull(inicio, "mesInicio é obrigatório");
        Objects.requireNonNull(fim, "mesFim é obrigatório");
        if (inicio.isAfter(fim)) {
            throw new IllegalArgumentException("mesInicio não pode ser depois de mesFim");
        }
        List<YearMonth> meses = new ArrayList<>();
        for (YearMonth mes = inicio; !mes.isAfter(fim); mes = mes.plusMonths(1)) {
            meses.add(mes);
            if (meses.size() > MAXIMO_MESES_EVOLUCAO) {
                throw new IllegalArgumentException("o período deve ter no máximo " + MAXIMO_MESES_EVOLUCAO + " meses");
            }
        }
        return meses;
    }

    /**
     * Duas consultas por mês (totais e categorias), com no máximo 12 meses. Para um app pessoal é simples e
     * suficiente; se pesar, dá para trocar por uma consulta agrupada por mês e categoria.
     */
    @Override
    public List<ResumoMes> comparativo(List<YearMonth> meses, Long contaId, Long categoriaId) {
        List<YearMonth> distintos = meses.stream().distinct().sorted().toList();
        if (distintos.size() < 2 || distintos.size() > MAXIMO_MESES_COMPARATIVO) {
            throw new IllegalArgumentException(
                    "informe de 2 a " + MAXIMO_MESES_COMPARATIVO + " meses diferentes para comparar");
        }
        return distintos.stream().map(mes -> {
            FiltroRelatorio filtro = new FiltroRelatorio(contaId, categoriaId, mes.atDay(1), mes.atEndOfMonth());
            return new ResumoMes(mes, resumo(filtro), porCategoria(filtro));
        }).toList();
    }

    @Override
    public PanoramaSaldos saldos() {
        // Já vem ordenado por conta e data do saldo (mais recente primeiro): o primeiro de cada conta vence.
        Map<Long, Importacao> maisRecentePorConta = new HashMap<>();
        for (Importacao importacao : importacaoRepository.findComSaldo(ResultadoImportacao.SUCESSO)) {
            maisRecentePorConta.putIfAbsent(importacao.getConta().getId(), importacao);
        }

        List<SaldoConta> contas = new ArrayList<>();
        BigDecimal consolidado = BigDecimal.ZERO.setScale(2);
        for (Conta conta : contaService.listar(true)) {
            Importacao ultima = maisRecentePorConta.get(conta.getId());
            BigDecimal saldo = ultima == null ? null : ultima.getSaldoDeclarado();
            contas.add(new SaldoConta(conta.getId(), conta.getApelido(), conta.getTipo(), saldo,
                    ultima == null ? null : ultima.getDataSaldo()));
            if (saldo != null) {
                consolidado = consolidado.add(saldo);
            }
        }
        return new PanoramaSaldos(consolidado, contas);
    }

    private static ResumoFinanceiro resumo(TotalCategoria linha) {
        return new ResumoFinanceiro(linha.receitas(), linha.despesas());
    }
}
