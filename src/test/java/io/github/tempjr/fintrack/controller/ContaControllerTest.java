package io.github.tempjr.fintrack.controller;

import io.github.tempjr.fintrack.exception.RecursoNaoEncontradoException;
import io.github.tempjr.fintrack.model.Conta;
import io.github.tempjr.fintrack.model.enums.Instituicao;
import io.github.tempjr.fintrack.model.enums.TipoConta;
import io.github.tempjr.fintrack.service.ContaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Camada web isolada: service mockado, sem banco. Confere status HTTP, JSON e formato dos erros. */
@WebMvcTest(ContaController.class)
@DisplayName("ContaController")
class ContaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContaService contaService;

    /** A entidade não tem setId (o banco gera); no teste, preenchemos por reflexão. */
    private static Conta contaComId(Long id, String apelido) {
        Conta conta = new Conta(Instituicao.ITAU, TipoConta.CORRENTE, apelido);
        ReflectionTestUtils.setField(conta, "id", id);
        return conta;
    }

    @Test
    @DisplayName("POST cria a conta: 201, Location e corpo")
    void criar() throws Exception {
        when(contaService.criar(Instituicao.ITAU, TipoConta.CORRENTE, "Itaú corrente"))
                .thenReturn(contaComId(1L, "Itaú corrente"));

        mockMvc.perform(post("/api/v1/contas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"instituicao": "ITAU", "tipo": "CORRENTE", "apelido": "Itaú corrente"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/contas/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.codigoInstituicao").value("0341"))
                .andExpect(jsonPath("$.moeda").value("BRL"))
                .andExpect(jsonPath("$.ativa").value(true))
                .andExpect(jsonPath("$.numeroExterno").doesNotExist());
    }

    @Test
    @DisplayName("POST com campos faltando: 400 ProblemDetail listando os campos")
    void criarInvalido() throws Exception {
        mockMvc.perform(post("/api/v1/contas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"instituicao": "ITAU", "apelido": " "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.campos.tipo").value("é obrigatório"))
                .andExpect(jsonPath("$.campos.apelido").value("é obrigatório"));
        verifyNoInteractions(contaService);
    }

    @Test
    @DisplayName("POST com instituição inexistente: 400 sem vazar a mensagem do Jackson")
    void criarInstituicaoInexistente() throws Exception {
        mockMvc.perform(post("/api/v1/contas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"instituicao": "BRADESCO", "tipo": "CORRENTE", "apelido": "X"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(
                        "Corpo da requisição inválido: JSON malformado ou valor não aceito."));
    }

    @Test
    @DisplayName("GET lista com filtro de ativas")
    void listar() throws Exception {
        when(contaService.listar(true)).thenReturn(List.of(contaComId(1L, "A"), contaComId(2L, "B")));

        mockMvc.perform(get("/api/v1/contas").param("ativa", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].apelido").value("B"));
    }

    @Test
    @DisplayName("GET de conta inexistente: 404 ProblemDetail")
    void buscarInexistente() throws Exception {
        when(contaService.buscarPorId(99L)).thenThrow(new RecursoNaoEncontradoException("Conta", 99L));

        mockMvc.perform(get("/api/v1/contas/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Conta com id 99 não existe."));
    }

    @Test
    @DisplayName("PATCH renomeia: 200 com a conta atualizada")
    void atualizar() throws Exception {
        when(contaService.renomear(1L, "Novo")).thenReturn(contaComId(1L, "Novo"));

        mockMvc.perform(patch("/api/v1/contas/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apelido": "Novo"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apelido").value("Novo"));
    }

    @Test
    @DisplayName("POST /inativar: 204 sem corpo")
    void inativar() throws Exception {
        mockMvc.perform(post("/api/v1/contas/1/inativar"))
                .andExpect(status().isNoContent());
        verify(contaService).inativar(1L);
    }

    @Test
    @DisplayName("erro inesperado: 500 com mensagem genérica, sem detalhes internos")
    void erroInesperado() throws Exception {
        doThrow(new IllegalStateException("detalhe interno")).when(contaService).inativar(any());

        mockMvc.perform(post("/api/v1/contas/1/inativar"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Ocorreu um erro inesperado. Tente novamente."));
    }
}
