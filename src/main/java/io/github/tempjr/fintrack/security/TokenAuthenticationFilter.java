package io.github.tempjr.fintrack.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Lê {@code Authorization: Bearer <token>} e, se o token for o configurado, marca a requisição como
 * autenticada (ADR-008, RNF11). Se não for, não faz nada: o Spring Security barra a requisição depois
 * com 401 (ver {@link ProblemDetailAuthenticationEntryPoint}).
 * <p>
 * Não é um {@code @Component} de propósito: se fosse, o Spring Boot o registraria também como filtro
 * comum do servlet, e ele rodaria duas vezes. Ele entra só na cadeia do Spring Security (SecurityConfig).
 */
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TokenAuthenticationFilter.class);
    private static final String PREFIXO = "Bearer ";
    /** Único usuário do sistema (P01). Se um dia virar multiusuário, isto vira o usuário do token. */
    static final String USUARIO = "fintrack";

    private final byte[] hashDoTokenEsperado;

    public TokenAuthenticationFilter(SegurancaProperties properties) {
        this.hashDoTokenEsperado = sha256(properties.token());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String cabecalho = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecalho != null) {
            if (tokenValido(cabecalho)) {
                SecurityContext contexto = SecurityContextHolder.createEmptyContext();
                contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        USUARIO, null, AuthorityUtils.createAuthorityList("ROLE_USUARIO")));
                SecurityContextHolder.setContext(contexto);
            } else {
                // Nunca logar o cabeçalho nem o token recebido (RNF08). IP e rota bastam para investigar.
                log.warn("Token inválido: {} {} de {}", request.getMethod(), request.getRequestURI(),
                        request.getRemoteAddr());
            }
        }
        chain.doFilter(request, response);
    }

    private boolean tokenValido(String cabecalho) {
        // O esquema ("Bearer") não diferencia maiúsculas (RFC 6750 / RFC 9110).
        if (cabecalho.length() <= PREFIXO.length()
                || !cabecalho.regionMatches(true, 0, PREFIXO, 0, PREFIXO.length())) {
            return false;
        }
        String recebido = cabecalho.substring(PREFIXO.length()).strip();
        // Compara os hashes com MessageDigest.isEqual: o tempo não depende de quantos caracteres batem
        // (equals pararia no primeiro diferente e vazaria o token aos poucos por medição de tempo), e
        // comparar hashes de tamanho fixo também não revela o tamanho do token.
        return MessageDigest.isEqual(hashDoTokenEsperado, sha256(recebido));
    }

    private static byte[] sha256(String texto) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
