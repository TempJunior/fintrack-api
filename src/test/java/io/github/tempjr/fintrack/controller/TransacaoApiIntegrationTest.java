package io.github.tempjr.fintrack.controller;

import io.github.tempjr.fintrack.model.Conta;
import io.github.tempjr.fintrack.model.Transacao;
import io.github.tempjr.fintrack.model.enums.Instituicao;
import io.github.tempjr.fintrack.model.enums.TipoConta;
import io.github.tempjr.fintrack.repository.ContaRepository;
import io.github.tempjr.fintrack.repository.TransacaoRepository;
import io.github.tempjr.fintrack.service.CategorizacaoService;
import io.github.tempjr.fintrack.service.ContaService;
import io.github.tempjr.fintrack.service.ImportService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API de transações de ponta a ponta (H2 + Flyway). Base: extrato sintético do Itaú (3 transações,
 * conta corrente) e do Nubank (2 transações, cartão), importados pelo fluxo real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Transações (integração pela API)")
class TransacaoApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ContaService contaService;
    @Autowired
    private ImportService importService;
    @Autowired
    private CategorizacaoService categorizacaoService;
    @Autowired
    private TransacaoRepository transacaoRepository;
    @Autowired
    private ContaRepository contaRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Long itau;
    private Long nubank;

    @BeforeEach
    void prepararBanco() {
        limparBanco();
        itau = contaService.criar(Instituicao.ITAU, TipoConta.CORRENTE, "Itaú").getId();
        nubank = contaService.criar(Instituicao.NUBANK, TipoConta.CARTAO, "Nubank cartão").getId();
        importar(itau, "itau-corrente-v1-cp1252.ofx");
        importar(nubank, "nubank-cartao-v2-utf8.ofx");
    }

    @AfterEach
    void limparBanco() {
        jdbc.execute("delete from transacao_tags");
        jdbc.execute("delete from transacoes");
        jdbc.execute("delete from importacoes");
        jdbc.execute("delete from regras_categorizacao");
        jdbc.execute("delete from categorias");
        jdbc.execute("delete from contas");
    }

    private void importar(Long conta, String arquivo) {
        try (InputStream in = getClass().getResourceAsStream("/ofx/" + arquivo)) {
            importService.importar(conta, arquivo, in.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Long idPorDescricao(String inicio) {
        return transacaoRepository.findAll().stream()
                .filter(t -> t.getDescricaoOriginal().startsWith(inicio))
                .findFirst().orElseThrow().getId();
    }

    private ResultActions listar(String query) throws Exception {
        return mockMvc.perform(get("/api/v1/transacoes" + query));
    }

    @Nested
    @DisplayName("listagem (RF12, RNF03)")
    class Listagem {

        @Test
        @DisplayName("sem filtro: todas, mais recentes primeiro, com página e dinheiro como string")
        void semFiltro() throws Exception {
            listar("")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElementos").value(5))
                    .andExpect(jsonPath("$.pagina").value(0))
                    .andExpect(jsonPath("$.ultima").value(true))
                    .andExpect(jsonPath("$.conteudo[0].data").value("2026-06-30"))
                    .andExpect(jsonPath("$.conteudo[0].descricao").value("PADARIA SÃO JOÃO – CAFÉ"))
                    .andExpect(jsonPath("$.conteudo[0].valor").value("-42.10"))
                    .andExpect(jsonPath("$.conteudo[0].tipo").value("DEBITO"))
                    .andExpect(jsonPath("$.conteudo[0].contaApelido").value("Itaú"));
        }

        @Test
        @DisplayName("paginação: tamanho 2 → 3 páginas")
        void paginacao() throws Exception {
            listar("?tamanho=2&pagina=1")
                    .andExpect(jsonPath("$.conteudo.length()").value(2))
                    .andExpect(jsonPath("$.totalPaginas").value(3))
                    .andExpect(jsonPath("$.ultima").value(false));
        }

        @Test
        @DisplayName("por conta")
        void porConta() throws Exception {
            listar("?contaId=" + nubank).andExpect(jsonPath("$.totalElementos").value(2));
        }

        @Test
        @DisplayName("por tipo (pelo sinal do valor)")
        void porTipo() throws Exception {
            listar("?tipo=DEBITO").andExpect(jsonPath("$.totalElementos").value(3));
            listar("?tipo=CREDITO").andExpect(jsonPath("$.totalElementos").value(2));
        }

        @Test
        @DisplayName("por período, com as duas datas inclusive")
        void porPeriodo() throws Exception {
            listar("?dataInicio=2026-06-01&dataFim=2026-06-02")
                    .andExpect(jsonPath("$.totalElementos").value(2))
                    .andExpect(jsonPath("$.conteudo[*].data", contains("2026-06-02", "2026-06-01")));
        }

        @Test
        @DisplayName("por texto, sem diferenciar maiúsculas; % digitado não vira curinga")
        void porTexto() throws Exception {
            listar("?texto=FARMÁCIA").andExpect(jsonPath("$.totalElementos").value(2));
            listar("?texto=%25").andExpect(jsonPath("$.totalElementos").value(0));
        }

        @Test
        @DisplayName("filtros combinados: conta + tipo + texto")
        void combinados() throws Exception {
            listar("?contaId=" + itau + "&tipo=DEBITO&texto=padaria")
                    .andExpect(jsonPath("$.totalElementos").value(1));
        }

        @Test
        @DisplayName("por categoria inclui as subcategorias; semCategoria traz o resto")
        void porCategoria() throws Exception {
            Long alimentacao = categorizacaoService.criarCategoria("Alimentação", null).getId();
            Long padaria = categorizacaoService.criarCategoria("Padaria", alimentacao).getId();
            mockMvc.perform(put("/api/v1/transacoes/" + idPorDescricao("PADARIA") + "/categoria")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"categoriaId\": " + padaria + "}"));

            listar("?categoriaId=" + alimentacao).andExpect(jsonPath("$.totalElementos").value(1));
            listar("?semCategoria=true").andExpect(jsonPath("$.totalElementos").value(4));
        }

        @Test
        @DisplayName("parâmetros inválidos: 400 com mensagem clara")
        void invalidos() throws Exception {
            listar("?tamanho=101")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.tamanho").value("deve ser no máximo 100"));
            listar("?tipo=XYZ")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Valor inválido para 'tipo'."));
            listar("?dataInicio=2026-07-01&dataFim=2026-06-01")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("dataInicio não pode ser depois de dataFim"));
        }
    }

    @Test
    @DisplayName("sem N+1: listar 20 transações com conta, categoria e tags faz no máximo 3 consultas")
    void semNMaisUm() throws Exception {
        Long categoria = categorizacaoService.criarCategoria("Diversos", null).getId();
        transactionTemplate.executeWithoutResult(status -> {
            Conta conta = contaRepository.findById(itau).orElseThrow();
            var cat = categorizacaoService.buscarCategoria(categoria);
            for (int i = 1; i <= 30; i++) {
                Transacao t = new Transacao(conta, "N1-" + i, LocalDate.of(2026, 7, 1).plusDays(i % 20),
                        new BigDecimal("-1.00"), "COMPRA " + i);
                t.categorizarManualmente(cat);
                t.adicionarTag("tag-" + i);
                transacaoRepository.save(t);
            }
        });
        Statistics estatisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();

        listar("?tamanho=20").andExpect(jsonPath("$.conteudo.length()").value(20));

        // 1 SELECT com JOIN em conta e categoria + 1 COUNT da paginação + 1 SELECT ... IN para as tags.
        // Com N+1 seriam 40+ (uma consulta de categoria e uma de tags por linha).
        assertThat(estatisticas.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    }

    @Nested
    @DisplayName("detalhe e enriquecimento (RF13, RF14, RF18)")
    class Enriquecimento {

        @Test
        @DisplayName("detalhe de transação inexistente: 404")
        void inexistente() throws Exception {
            mockMvc.perform(get("/api/v1/transacoes/999999")).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("categoria manual: origem MANUAL; remover volta a Não categorizado")
        void categoriaManual() throws Exception {
            Long id = idPorDescricao("PADARIA");
            Long alimentacao = categorizacaoService.criarCategoria("Alimentação", null).getId();

            mockMvc.perform(put("/api/v1/transacoes/" + id + "/categoria")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"categoriaId\": " + alimentacao + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.categoriaNome").value("Alimentação"))
                    .andExpect(jsonPath("$.origemCategoria").value("MANUAL"));

            mockMvc.perform(delete("/api/v1/transacoes/" + id + "/categoria"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.categoriaId").doesNotExist())
                    .andExpect(jsonPath("$.origemCategoria").doesNotExist());
        }

        @Test
        @DisplayName("PATCH altera nota, tags e transferência interna")
        void patchAlteraCampos() throws Exception {
            Long id = idPorDescricao("SALÁRIO");

            mockMvc.perform(patch("/api/v1/transacoes/" + id).contentType(MediaType.APPLICATION_JSON).content("""
                            {"nota": "  adiantamento  ", "tags": ["Trabalho", "renda", "trabalho"], "transferenciaInterna": true}
                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nota").value("adiantamento"))
                    .andExpect(jsonPath("$.tags", contains("renda", "trabalho")))
                    .andExpect(jsonPath("$.transferenciaInterna").value(true));
        }

        @Test
        @DisplayName("PATCH parcial: campo ausente não muda")
        void patchParcial() throws Exception {
            Long id = idPorDescricao("SALÁRIO");
            mockMvc.perform(patch("/api/v1/transacoes/" + id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nota\": \"x\", \"tags\": [\"renda\"]}"));

            mockMvc.perform(patch("/api/v1/transacoes/" + id).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"transferenciaInterna\": true}"))
                    .andExpect(jsonPath("$.nota").value("x"))
                    .andExpect(jsonPath("$.tags", contains("renda")));
        }

        @Test
        @DisplayName("PATCH não altera o dado bruto, mesmo que o cliente mande os campos (RNF06)")
        void dadoBrutoImutavel() throws Exception {
            Long id = idPorDescricao("SALÁRIO");

            mockMvc.perform(patch("/api/v1/transacoes/" + id).contentType(MediaType.APPLICATION_JSON).content("""
                            {"valor": "1.00", "descricao": "HACK", "data": "2000-01-01", "nota": "ok"}
                            """))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/transacoes/" + id))
                    .andExpect(jsonPath("$.valor").value("3500.00"))
                    .andExpect(jsonPath("$.descricao").value("SALÁRIO EMPRESA FICTÍCIA"))
                    .andExpect(jsonPath("$.data").value("2026-06-05"))
                    .andExpect(jsonPath("$.nota").value("ok"));
        }

        @Test
        @DisplayName("tag com mais de 40 caracteres: 400")
        void tagLonga() throws Exception {
            mockMvc.perform(patch("/api/v1/transacoes/" + idPorDescricao("SALÁRIO"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tags\": [\"" + "x".repeat(41) + "\"]}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
