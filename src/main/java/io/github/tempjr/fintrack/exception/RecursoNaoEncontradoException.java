package io.github.tempjr.fintrack.exception;

/** Recurso pedido não existe. Vira HTTP 404 com ProblemDetail na Etapa 2. */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String recurso, Long id) {
        super(recurso + " com id " + id + " não existe.");
    }
}
