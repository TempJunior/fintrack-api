package io.github.tempjr.fintrack.exception;

/** A operação entra em conflito com dados existentes (ex.: nome repetido). Vira HTTP 409. */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
