package io.github.tempjr.fintrack.exception;

/** O arquivo não pôde ser lido: formato desconhecido, estrutura inválida ou conteúdo corrompido (RF05, RF09). */
public class ArquivoInvalidoException extends ImportacaoRejeitadaException {

    public ArquivoInvalidoException(String mensagem) {
        super(mensagem);
    }

    public ArquivoInvalidoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
