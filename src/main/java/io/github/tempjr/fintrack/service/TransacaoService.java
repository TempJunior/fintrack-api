package io.github.tempjr.fintrack.service;

import io.github.tempjr.fintrack.model.Conta;
import io.github.tempjr.fintrack.model.Transacao;
import io.github.tempjr.fintrack.repository.TransacaoFiltro;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** Transações (RF07, RF11–RF14). É o único "dono" do TransacaoRepository. */
public interface TransacaoService {

    /**
     * Grava só as transações que ainda não existem na conta (RF07, RNF04).
     * Reimportar o mesmo arquivo, ou um período sobreposto, não gera duplicata.
     *
     * @param conta      conta de destino; todas as transações precisam ser dela
     * @param transacoes transações lidas do arquivo, ainda não gravadas
     * @throws IllegalArgumentException se alguma transação for de outra conta
     */
    ResultadoDedup salvarNovas(Conta conta, List<Transacao> transacoes);

    /**
     * Reaplica as regras atuais em todas as transações sem categoria ou categorizadas por regra (RF19).
     * Escolhas manuais nunca são tocadas (RF18, D-11). Se nenhuma regra casa mais com uma transação
     * categorizada por regra, ela volta a "Não categorizado" (RF20).
     */
    ResultadoReaplicacao reaplicarRegras();

    /**
     * Listagem paginada com filtros combináveis (RF12, RNF03).
     * Devolve as transações com conta, categoria e tags já carregadas.
     */
    Page<Transacao> listar(TransacaoFiltro filtro, Pageable pageable);

    /** Transação com conta, categoria e tags carregadas. */
    Transacao buscar(Long id);

    /** Categoria escolhida pelo usuário: tem prioridade sobre as regras (RF18). */
    Transacao categorizarManualmente(Long id, Long categoriaId);

    /** Volta a "Não categorizado" (RF20). As regras poderão categorizá-la de novo no próximo lote. */
    Transacao removerCategoria(Long id);

    /**
     * Enriquecimento (RF13, RF14). Parâmetro nulo = não muda.
     *
     * @param nota  texto vazio apaga a nota
     * @param tags  substitui todas as tags; lista vazia apaga
     */
    Transacao enriquecer(Long id, String nota, List<String> tags, Boolean transferenciaInterna);
}
