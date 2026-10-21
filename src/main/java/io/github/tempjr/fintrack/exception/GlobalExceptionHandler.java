package io.github.tempjr.fintrack.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converte exceções em respostas Problem Details (RFC 9457). O app Android exibe o campo "detail"
 * (arquitetura §8.3).
 * <p>
 * Estender ResponseEntityExceptionHandler faz os erros do próprio Spring MVC (rota inexistente,
 * método não suportado, parâmetro com tipo errado...) também saírem como ProblemDetail.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail handleNaoEncontrado(RecursoNaoEncontradoException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Recurso não encontrado");
        return problem;
    }

    /** Importação recusada: arquivo inválido ou de outra conta (RF09). Nada foi gravado. */
    @ExceptionHandler(ImportacaoRejeitadaException.class)
    public ProblemDetail handleImportacaoRejeitada(ImportacaoRejeitadaException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Importação rejeitada");
        return problem;
    }

    /** Constraint do banco violada. Na importação: duas importações simultâneas do mesmo arquivo. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleConflito(DataIntegrityViolationException ex) {
        log.warn("Violação de integridade no banco: {}", ex.getClass().getSimpleName());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Os dados mudaram enquanto a operação era feita. Tente novamente.");
        problem.setTitle("Conflito");
        return problem;
    }

    /** Regras das entidades (ex.: apelido vazio, valor com 3 casas) lançam IllegalArgumentException. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleArgumentoInvalido(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Requisição inválida");
        return problem;
    }

    /** Erro inesperado: detalhe completo só no log; o cliente recebe mensagem genérica. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleInesperado(Exception ex) {
        log.error("Erro inesperado", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocorreu um erro inesperado. Tente novamente.");
        problem.setTitle("Erro interno");
        return problem;
    }

    /** Bean Validation (@Valid): lista os campos com problema em "detail" e em "campos". */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> campos = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(erro -> campos.putIfAbsent(erro.getField(), erro.getDefaultMessage()));

        String detail = campos.entrySet().stream()
                .map(e -> e.getKey() + " " + e.getValue())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Dados inválidos");

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Dados inválidos");
        problem.setProperty("campos", campos);
        return ResponseEntity.badRequest().body(problem);
    }

    /** JSON malformado ou valor de enum inexistente (ex.: "instituicao": "BRADESCO"). */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        // A mensagem original do Jackson expõe detalhes internos; não repassamos.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Corpo da requisição inválido: JSON malformado ou valor não aceito.");
        problem.setTitle("Requisição inválida");
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
                                                                          HttpHeaders headers,
                                                                          HttpStatusCode status,
                                                                          WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
                "O arquivo passa do tamanho máximo de 5 MB.");
        problem.setTitle("Arquivo grande demais");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(MissingServletRequestPartException ex,
                                                                     HttpHeaders headers,
                                                                     HttpStatusCode status,
                                                                     WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Envie o arquivo no campo '" + ex.getRequestPartName() + "'.");
        problem.setTitle("Arquivo não enviado");
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
                                                             HttpHeaders headers, HttpStatusCode statusCode,
                                                             WebRequest request) {
        if (statusCode.is5xxServerError()) {
            log.error("Erro interno no Spring MVC", ex);
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }
}
