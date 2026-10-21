package io.github.tempjr.fintrack.security;

import io.github.tempjr.fintrack.config.TokenDeTesteConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Acesso à API pela borda (ADR-008, RNF11, RNF14). Sem o token automático dos outros testes. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Segurança (integração)")
class SegurancaIntegrationTest {

    private static final String BEARER = "Bearer " + TokenDeTesteConfig.TOKEN;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("sem token: 401 em ProblemDetail, com WWW-Authenticate")
    void semToken() throws Exception {
        mockMvc.perform(get("/api/v1/contas"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Token de acesso ausente ou inválido. Confira a configuração do app."));
    }

    @ParameterizedTest(name = "Authorization: \"{0}\" → 401")
    @ValueSource(strings = {
            "Bearer token-errado-com-pelo-menos-32-caracteres!!",
            "Bearer ",
            "Basic dXNlcjpwYXNz",
            TokenDeTesteConfig.TOKEN, // sem o "Bearer "
    })
    @DisplayName("token errado ou cabeçalho malformado: 401")
    void tokenInvalido(String cabecalho) throws Exception {
        mockMvc.perform(get("/api/v1/contas").header(HttpHeaders.AUTHORIZATION, cabecalho))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("token correto: acesso liberado; esquema 'bearer' em minúsculas também vale")
    void tokenCorreto() throws Exception {
        mockMvc.perform(get("/api/v1/contas").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/contas").header(HttpHeaders.AUTHORIZATION, "bearer " + TokenDeTesteConfig.TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("escrita também exige token (sem CSRF, mas com token)")
    void escritaSemToken() throws Exception {
        mockMvc.perform(post("/api/v1/contas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instituicao\": \"ITAU\", \"tipo\": \"CORRENTE\", \"apelido\": \"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("health check aberto e sem detalhes internos (RNF14)")
    void healthCheck() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    @DisplayName("outros endpoints do actuator não estão expostos")
    void actuatorFechado() throws Exception {
        mockMvc.perform(get("/actuator/env").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/beans").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sem sessão: nenhuma resposta cria cookie")
    void semSessao() throws Exception {
        mockMvc.perform(get("/api/v1/contas").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("401 não revela o token esperado")
    void naoVazaToken() throws Exception {
        mockMvc.perform(get("/api/v1/contas").header(HttpHeaders.AUTHORIZATION, "Bearer x"))
                .andExpect(content().string(not(containsString(TokenDeTesteConfig.TOKEN))));
    }
}
