package io.github.tempjr.fintrack.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados da especificação OpenAPI (RNF22). A especificação é o contrato com o app Android.
 * Spec em /v3/api-docs; Swagger UI em /swagger-ui.html.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fintrackOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Fintrack API")
                .version("v1")
                .description("""
                        API do Fintrack. Valores monetários trafegam como string decimal (ex.: "-123.45"); \
                        datas em ISO-8601 (yyyy-MM-dd); erros no formato Problem Details (RFC 9457)."""));
    }
}
