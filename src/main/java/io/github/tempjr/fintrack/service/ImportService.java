package io.github.tempjr.fintrack.service;

import io.github.tempjr.fintrack.model.Importacao;

import java.util.List;

/** Importação de extratos (RF04–RF09, RNF05, RNF12). Fluxo B da arquitetura (§2, §7). */
public interface ImportService {

    /**
     * Importa um extrato para a conta: tudo ou nada (RNF05). Toda tentativa gera registro de auditoria,
     * inclusive as rejeitadas (RNF12).
     *
     * @param nomeArquivo nome original do arquivo; só para o histórico
     * @param conteudo    bytes do arquivo
     * @return o registro da importação com o resumo (RF08)
     * @throws io.github.tempjr.fintrack.exception.RecursoNaoEncontradoException se a conta não existir
     * @throws io.github.tempjr.fintrack.exception.ImportacaoRejeitadaException  se o arquivo ou a conta não
     *                                                                            permitirem a importação
     */
    Importacao importar(Long contaId, String nomeArquivo, byte[] conteudo);

    /** Histórico da conta, mais recente primeiro. */
    List<Importacao> listar(Long contaId);

    Importacao buscar(Long contaId, Long importacaoId);
}
