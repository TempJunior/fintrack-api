package io.github.tempjr.fintrack.controller;

import io.github.tempjr.fintrack.exception.ImportacaoRejeitadaException;
import io.github.tempjr.fintrack.exception.RecursoNaoEncontradoException;
import io.github.tempjr.fintrack.model.Conta;
import io.github.tempjr.fintrack.model.Importacao;
import io.github.tempjr.fintrack.model.enums.Instituicao;
import io.github.tempjr.fintrack.model.enums.StatementFormat;
import io.github.tempjr.fintrack.model.enums.TipoConta;
import io.github.tempjr.fintrack.service.ImportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImportController.class)
@DisplayName("ImportController")
class ImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImportService importService;

    private final MockMultipartFile arquivo =
            new MockMultipartFile("arquivo", "extrato.ofx", "application/octet-stream", "OFXHEADER:100".getBytes());

    private static Importacao importacaoComId(Long id) {
        Conta conta = new Conta(Instituicao.ITAU, TipoConta.CORRENTE, "Itaú");
        Importacao importacao = Importacao.sucesso(conta, "extrato.ofx", StatementFormat.OFX_V1,
                3, 2, 1, new BigDecimal("4807.15"), LocalDate.of(2026, 6, 30));
        ReflectionTestUtils.setField(importacao, "id", id);
        return importacao;
    }

    @Test
    @DisplayName("POST multipart: 201, Location e resumo com saldo como string decimal")
    void importa() throws Exception {
        when(importService.importar(eq(1L), eq("extrato.ofx"), any())).thenReturn(importacaoComId(10L));

        mockMvc.perform(multipart("/api/v1/contas/1/importacoes").file(arquivo))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/contas/1/importacoes/10"))
                .andExpect(jsonPath("$.resultado").value("SUCESSO"))
                .andExpect(jsonPath("$.totalLido").value(3))
                .andExpect(jsonPath("$.novas").value(2))
                .andExpect(jsonPath("$.duplicadas").value(1))
                .andExpect(jsonPath("$.saldoDeclarado").value("4807.15")) // string, não número
                .andExpect(jsonPath("$.dataSaldo").value("2026-06-30"));
    }

    @Test
    @DisplayName("importação rejeitada: 422 com a mensagem para o usuário")
    void rejeitada() throws Exception {
        when(importService.importar(eq(1L), any(), any()))
                .thenThrow(new ImportacaoRejeitadaException("O arquivo é de outra conta. Confira se escolheu a conta certa."));

        mockMvc.perform(multipart("/api/v1/contas/1/importacoes").file(arquivo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Importação rejeitada"))
                .andExpect(jsonPath("$.detail").value("O arquivo é de outra conta. Confira se escolheu a conta certa."));
    }

    @Test
    @DisplayName("sem o campo 'arquivo': 400 explicando o que falta")
    void semArquivo() throws Exception {
        mockMvc.perform(multipart("/api/v1/contas/1/importacoes"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Envie o arquivo no campo 'arquivo'."));
        verifyNoInteractions(importService);
    }

    @Test
    @DisplayName("conta inexistente: 404")
    void contaInexistente() throws Exception {
        when(importService.importar(eq(99L), any(), any())).thenThrow(new RecursoNaoEncontradoException("Conta", 99L));

        mockMvc.perform(multipart("/api/v1/contas/99/importacoes").file(arquivo))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET histórico")
    void historico() throws Exception {
        when(importService.listar(1L)).thenReturn(List.of(importacaoComId(11L), importacaoComId(10L)));

        mockMvc.perform(get("/api/v1/contas/1/importacoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(11));
    }
}
