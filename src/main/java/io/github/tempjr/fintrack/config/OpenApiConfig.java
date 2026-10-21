package io.github.tempjr.fintrack.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados da especificação OpenAPI (RNF22). A especificação é o contrato com o app Android.
 * Spec em /v3/api-docs; Swagger UI em /swagger-ui.html (desligados no perfil prod).
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_TOKEN = "token";

    @Bean
    public OpenAPI fintrackOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Fintrack API")
                        .version("v1")
                        .description("""
                                API do Fintrack. Toda rota exige `Authorization: Bearer <token>` (exceto o health \
                                check); sem token válido, 401. Valores monetários trafegam como string decimal \
                                (ex.: "-123.45"); datas em ISO-8601 (yyyy-MM-dd); erros no formato Problem Details \
                                (RFC 9457)."""))
                // Botão "Authorize" no Swagger UI e o requisito de token em todas as operações.
                .components(new Components().addSecuritySchemes(ESQUEMA_TOKEN, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .description("Token único configurado em FINTRACK_TOKEN (ADR-008)")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_TOKEN));
    }
}
