package io.github.tempjr.fintrack.exception;

/**
 * A importação foi recusada e nada foi gravado (RF09, RNF05). Vira HTTP 422.
 * A mensagem vai para o usuário: precisa ser clara e nunca conter número de conta nem valores (RNF08).
 */
public class ImportacaoRejeitadaException extends RuntimeException {

    public ImportacaoRejeitadaException(String mensagem) {
        super(mensagem);
    }

    public ImportacaoRejeitadaException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
