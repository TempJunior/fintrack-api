package io.github.tempjr.fintrack.controller;

import com.jayway.jsonpath.JsonPath;
import io.github.tempjr.fintrack.model.Transacao;
import io.github.tempjr.fintrack.model.enums.Instituicao;
import io.github.tempjr.fintrack.model.enums.OrigemCategoria;
import io.github.tempjr.fintrack.model.enums.TipoConta;
import io.github.tempjr.fintrack.repository.TransacaoRepository;
import io.github.tempjr.fintrack.service.CategorizacaoService;
import io.github.tempjr.fintrack.service.ContaService;
import io.github.tempjr.fintrack.service.ImportService;
import io.github.tempjr.fintrack.config.TokenDeTesteConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Categorização de ponta a ponta: HTTP → controller → service → banco (H2 + Flyway).
 * Sem transação de teste: o banco é limpo antes e depois de cada teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TokenDeTesteConfig.class)
@ActiveProfiles("test")
@DisplayName("Categorização (integração pela API)")
class CategorizacaoApiIntegrationTest {

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
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    @AfterEach
    void limparBanco() {
        jdbc.execute("delete from transacao_tags");
        jdbc.execute("delete from transacoes");
        jdbc.execute("delete from importacoes");
        jdbc.execute("delete from regras_categorizacao");
        jdbc.execute("delete from categorias");
        jdbc.execute("delete from contas");
    }

    private long criar(String url, String json) throws Exception {
        String resposta = mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    private long criarCategoria(String nome, Long paiId) throws Exception {
        String pai = paiId == null ? "" : ", \"categoriaPaiId\": " + paiId;
        return criar("/api/v1/categorias", "{\"nome\": \"" + nome + "\"" + pai + "}");
    }

    private long criarRegra(long categoriaId, String operador, String padrao, int prioridade) throws Exception {
        return criar("/api/v1/regras", """
                {"categoriaId": %d, "operador": "%s", "padrao": "%s", "prioridade": %d}
                """.formatted(categoriaId, operador, padrao, prioridade));
    }

    private Long importarItau() {
        Long conta = contaService.criar(Instituicao.ITAU, TipoConta.CORRENTE, "Itaú").getId();
        try (InputStream in = getClass().getResourceAsStream("/ofx/itau-corrente-v1-cp1252.ofx")) {
            importService.importar(conta, "itau.ofx", in.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return conta;
    }

    /** Categoria (nome) de cada transação, por descrição. Lido numa transação por causa do LAZY. */
    private Map<String, String> categoriasPorDescricao() {
        return transactionTemplate.execute(status -> transacaoRepository.findAll().stream()
                .collect(Collectors.toMap(Transacao::getDescricaoOriginal,
                        t -> t.getCategoria() == null ? "-" : t.getCategoria().getNome())));
    }

    @Test
    @DisplayName("lista categorias em árvore, com subcategorias dentro da raiz")
    void arvore() throws Exception {
        long alimentacao = criarCategoria("Alimentação", null);
        criarCategoria("Restaurante", alimentacao);
        criarCategoria("Padaria", alimentacao);
        criarCategoria("Transporte", null);

        mockMvc.perform(get("/api/v1/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nome").value("Alimentação"))
                .andExpect(jsonPath("$[0].subcategorias[*].nome").value(org.hamcrest.Matchers.contains("Padaria", "Restaurante")))
                .andExpect(jsonPath("$[1].nome").value("Transporte"))
                .andExpect(jsonPath("$[1].subcategorias.length()").value(0));
    }

    @Test
    @DisplayName("subcategoria lida sozinha traz o id do pai (proxy LAZY fora da transação)")
    void subcategoriaComPai() throws Exception {
        long alimentacao = criarCategoria("Alimentação", null);
        long restaurante = criarCategoria("Restaurante", alimentacao);

        mockMvc.perform(get("/api/v1/categorias/" + restaurante))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriaPaiId").value(alimentacao));
    }

    @Test
    @DisplayName("nome repetido no mesmo nível (sem diferenciar maiúsculas): 409; em outro nível pode")
    void nomeRepetido() throws Exception {
        long alimentacao = criarCategoria("Alimentação", null);

        mockMvc.perform(post("/api/v1/categorias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"alimentação\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Já existe a categoria \"alimentação\" neste nível."));

        criarCategoria("Alimentação", alimentacao); // como subcategoria, é outro nível
    }

    @Test
    @DisplayName("terceiro nível e regex inválida: 400")
    void validacoes() throws Exception {
        long alimentacao = criarCategoria("Alimentação", null);
        long restaurante = criarCategoria("Restaurante", alimentacao);

        mockMvc.perform(post("/api/v1/categorias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Japonês\", \"categoriaPaiId\": " + restaurante + "}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/regras").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoriaId\": " + alimentacao + ", \"operador\": \"REGEX\", \"padrao\": \"PIX (\", \"prioridade\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("padrão não é uma expressão regular válida"));
    }

    @Test
    @DisplayName("importação já categoriza pelas regras existentes (RF17)")
    void importacaoCategoriza() throws Exception {
        long alimentacao = criarCategoria("Alimentação", null);
        long moradia = criarCategoria("Moradia", null);
        criarRegra(alimentacao, "CONTAINS", "padaria", 10);
        criarRegra(moradia, "STARTS_WITH", "pagto agua", 10); // sem acento casa com "ÁGUA"

        importarItau();

        assertThat(categoriasPorDescricao()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "PAGTO ÁGUA E ESGOTO", "Moradia",
                "SALÁRIO EMPRESA FICTÍCIA", "-",
                "PADARIA SÃO JOÃO – CAFÉ", "Alimentação"));
    }

    @Test
    @DisplayName("reaplicar em lote: categoriza o histórico, preserva a manual e limpa o que não casa mais (RF19, D-11)")
    void reaplicar() throws Exception {
        importarItau(); // sem regras: tudo "Não categorizado"
        long alimentacao = criarCategoria("Alimentação", null);
        long renda = criarCategoria("Renda", null);
        long trabalho = criarCategoria("Trabalho", null);

        // Escolha manual na transação da padaria (o endpoint de enriquecimento vem na Etapa 6).
        transactionTemplate.executeWithoutResult(status -> transacaoRepository.findAll().stream()
                .filter(t -> t.getDescricaoOriginal().startsWith("PADARIA"))
                .forEach(t -> t.categorizarManualmente(categorizacaoService.buscarCategoria(trabalho))));

        criarRegra(alimentacao, "CONTAINS", "padaria", 10);
        long regraSalario = criarRegra(renda, "CONTAINS", "salario", 10);

        mockMvc.perform(post("/api/v1/regras/reaplicar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avaliadas").value(2)) // a manual nem entra
                .andExpect(jsonPath("$.alteradas").value(1));
        assertThat(categoriasPorDescricao())
                .containsEntry("SALÁRIO EMPRESA FICTÍCIA", "Renda")
                .containsEntry("PADARIA SÃO JOÃO – CAFÉ", "Trabalho"); // manual preservada

        // Excluir a regra e reaplicar: o salário volta a "Não categorizado".
        mockMvc.perform(delete("/api/v1/regras/" + regraSalario)).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/regras/reaplicar"))
                .andExpect(jsonPath("$.alteradas").value(1));
        assertThat(categoriasPorDescricao()).containsEntry("SALÁRIO EMPRESA FICTÍCIA", "-");
        assertThat(transacaoRepository.findAll())
                .filteredOn(t -> t.getDescricaoOriginal().startsWith("PADARIA"))
                .extracting(Transacao::getOrigemCategoria)
                .containsExactly(OrigemCategoria.MANUAL);
    }

    @Test
    @DisplayName("lista regras em ordem de avaliação, com o nome da categoria")
    void listarRegras() throws Exception {
        long alimentacao = criarCategoria("Alimentação", null);
        criarRegra(alimentacao, "CONTAINS", "mercado", 20);
        criarRegra(alimentacao, "CONTAINS", "padaria", 5);

        mockMvc.perform(get("/api/v1/regras"))
                .andExpect(jsonPath("$[0].padrao").value("padaria"))
                .andExpect(jsonPath("$[0].categoriaNome").value("Alimentação"))
                .andExpect(jsonPath("$[1].padrao").value("mercado"));
    }

    @Test
    @DisplayName("renomear categoria")
    void renomear() throws Exception {
        long id = criarCategoria("Alimentacao", null);

        mockMvc.perform(patch("/api/v1/categorias/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Alimentação\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Alimentação"));
    }
}
