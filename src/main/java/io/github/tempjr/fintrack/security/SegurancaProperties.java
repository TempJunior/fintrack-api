package io.github.tempjr.fintrack.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração de acesso (ADR-008). O token vem de variável de ambiente (RNF09):
 * {@code fintrack.seguranca.token: ${FINTRACK_TOKEN}}.
 * <p>
 * Validado na subida: sem token, ou com token curto, a aplicação não inicia. Melhor falhar no deploy
 * do que subir aberta ou com uma senha fraca. As mensagens nunca incluem o valor do token (RNF08).
 */
@ConfigurationProperties(prefix = "fintrack.seguranca")
public record SegurancaProperties(String token) {

    static final int TAMANHO_MINIMO_TOKEN = 32;

    public SegurancaProperties {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "fintrack.seguranca.token não configurado. Defina a variável de ambiente FINTRACK_TOKEN.");
        }
        if (token.strip().length() < TAMANHO_MINIMO_TOKEN) {
            throw new IllegalStateException("fintrack.seguranca.token muito curto: use pelo menos "
                    + TAMANHO_MINIMO_TOKEN + " caracteres aleatórios.");
        }
        token = token.strip();
    }

    /** Sem o valor do token: toString pode acabar em log. */
    @Override
    public String toString() {
        return "SegurancaProperties{token=***}";
    }
}
