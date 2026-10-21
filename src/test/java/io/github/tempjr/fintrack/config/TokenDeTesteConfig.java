package io.github.tempjr.fintrack.config;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Coloca o token de teste em toda requisição do MockMvc, para os testes da API não repetirem o cabeçalho.
 * O valor é o mesmo de application-test.yaml. Os testes de segurança não importam esta classe.
 */
@TestConfiguration
public class TokenDeTesteConfig {

    public static final String TOKEN = "token-de-teste-com-pelo-menos-32-caracteres";

    @Bean
    public MockMvcBuilderCustomizer tokenEmTodaRequisicao() {
        return builder -> builder.defaultRequest(
                MockMvcRequestBuilders.get("/").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN));
    }
}
