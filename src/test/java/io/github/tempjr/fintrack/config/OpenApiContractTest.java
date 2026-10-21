package io.github.tempjr.fintrack.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A especificação OpenAPI é gerada e contém os endpoints (RNF22, definição de pronto). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TokenDeTesteConfig.class)
@ActiveProfiles("test")
@DisplayName("Contrato OpenAPI")
class OpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("/v3/api-docs expõe todos os endpoints do MVP até aqui")
    void especificacaoTemContas() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Fintrack API"))
                .andExpect(jsonPath("$.paths['/api/v1/contas'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/contas'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/contas/{id}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/contas/{id}/inativar'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/contas/{contaId}/importacoes'].post.requestBody.content['multipart/form-data']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/contas/{contaId}/importacoes'].get").exists())
                .andExpect(jsonPath("$.components.schemas.ImportacaoResponse.properties.saldoDeclarado.type").value("string"))
                .andExpect(jsonPath("$.paths['/api/v1/categorias'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/categorias/{id}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/regras'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/regras/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/v1/regras/reaplicar'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/transacoes'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/transacoes/{id}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/transacoes/{id}/categoria'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/transacoes/{id}/categoria'].delete").exists())
                .andExpect(jsonPath("$.components.schemas.TransacaoResponse.properties.valor.type").value("string"))
                .andExpect(jsonPath("$.paths['/api/v1/relatorios/resumo'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/relatorios/por-categoria'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/relatorios/mensal'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/relatorios/comparativo'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/relatorios/saldos'].get").exists());
    }
}
