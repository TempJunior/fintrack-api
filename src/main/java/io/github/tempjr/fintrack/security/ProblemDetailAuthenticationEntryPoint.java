package io.github.tempjr.fintrack.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.net.URI;

/**
 * Resposta 401 no mesmo formato dos outros erros da API (ProblemDetail, RFC 9457).
 * O app trata 401 levando o usuário à tela de configuração (arquitetura §8.3).
 * A mensagem é a mesma para token ausente e errado: não ajuda quem está tentando adivinhar.
 */
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public ProblemDetailAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Token de acesso ausente ou inválido. Confira a configuração do app.");
        problem.setTitle("Não autorizado");
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        // Sem setCharacterEncoding: JSON é sempre UTF-8 e o Jackson já escreve assim no OutputStream.
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
