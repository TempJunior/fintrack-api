package io.github.tempjr.fintrack.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Perfil prod: documentação e console do H2 desligados (arquitetura §9.3).
 * O banco do prod é Postgres (ainda pendente, 1.3); aqui o datasource é trocado por H2 só para subir o contexto.
 */
@SpringBootTest(properties = {
        "FINTRACK_TOKEN=token-de-producao-de-teste-com-32-caracteres",
        "FINTRACK_DB_URL=jdbc:h2:mem:fintrack-prod-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "FINTRACK_DB_USER=sa",
        "FINTRACK_DB_PASSWORD="
})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@DisplayName("Perfil prod")
class PerfilProdTest {

    private static final String BEARER = "Bearer token-de-producao-de-teste-com-32-caracteres";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("sem Swagger nem especificação OpenAPI, mesmo com token")
    void semDocumentacao() throws Exception {
        mockMvc.perform(get("/v3/api-docs").header(HttpHeaders.AUTHORIZATION, BEARER)).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui.html").header(HttpHeaders.AUTHORIZATION, BEARER)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sem console do H2")
    void semConsoleH2() throws Exception {
        mockMvc.perform(get("/h2-console").header(HttpHeaders.AUTHORIZATION, BEARER)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("health check continua aberto; API continua exigindo token")
    void healthEApi() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/contas")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/contas").header(HttpHeaders.AUTHORIZATION, BEARER)).andExpect(status().isOk());
    }
}
