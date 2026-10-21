package io.github.tempjr.fintrack.service;

import io.github.tempjr.fintrack.model.Conta;
import io.github.tempjr.fintrack.model.Transacao;

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
}
