package io.github.tempjr.fintrack.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A especificação OpenAPI é gerada e contém os endpoints (RNF22, definição de pronto). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Contrato OpenAPI")
class OpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("/v3/api-docs expõe os endpoints de contas")
    void especificacaoTemContas() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Fintrack API"))
                .andExpect(jsonPath("$.paths['/api/v1/contas'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/contas'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/contas/{id}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/contas/{id}/inativar'].post").exists());
    }
}
