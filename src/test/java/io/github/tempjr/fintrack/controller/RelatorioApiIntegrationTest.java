package io.github.tempjr.fintrack.controller;

import io.github.tempjr.fintrack.model.Conta;
import io.github.tempjr.fintrack.model.Transacao;
import io.github.tempjr.fintrack.model.enums.Instituicao;
import io.github.tempjr.fintrack.model.enums.Operador;
import io.github.tempjr.fintrack.model.enums.TipoConta;
import io.github.tempjr.fintrack.repository.ContaRepository;
import io.github.tempjr.fintrack.repository.TransacaoRepository;
import io.github.tempjr.fintrack.service.CategorizacaoService;
import io.github.tempjr.fintrack.service.ContaService;
import io.github.tempjr.fintrack.service.ImportService;
import io.github.tempjr.fintrack.service.TransacaoService;
import io.github.tempjr.fintrack.config.TokenDeTesteConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Relatórios de ponta a ponta sobre os extratos sintéticos:
 * <pre>
 * Itaú (corrente):  02/06 -150.75 PAGTO ÁGUA · 05/06 +3500.00 SALÁRIO · 30/06 -42.10 PADARIA   saldo 4807.15 em 30/06
 * Nubank (cartão):  01/06  -89.90 Farmácia   · 20/06   +89.90 Estorno Farmácia               saldo -1234.56 em 30/06
 * </pre>
 * Totais de junho: receitas 3589.90, despesas 282.75, saldo 3307.15.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TokenDeTesteConfig.class)
@ActiveProfiles("test")
@DisplayName("Relatórios (integração pela API)")
class RelatorioApiIntegrationTest {

    private static final Charset CP1252 = Charset.forName("windows-1252");

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
    private TransacaoService transacaoService;
    @Autowired
    private TransacaoRepository transacaoRepository;
    @Autowired
    private ContaRepository contaRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long itau;
    private Long nubank;

    @BeforeEach
    void prepararBanco() {
        limparBanco();
        itau = contaService.criar(Instituicao.ITAU, TipoConta.CORRENTE, "Itaú").getId();
        nubank = contaService.criar(Instituicao.NUBANK, TipoConta.CARTAO, "Nubank cartão").getId();
        importService.importar(itau, "itau.ofx", fixture("itau-corrente-v1-cp1252.ofx"));
        importService.importar(nubank, "nubank.ofx", fixture("nubank-cartao-v2-utf8.ofx"));
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

    private static byte[] fixture(String nome) {
        try (InputStream in = RelatorioApiIntegrationTest.class.getResourceAsStream("/ofx/" + nome)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private ResultActions relatorio(String caminho) throws Exception {
        return mockMvc.perform(get("/api/v1/relatorios" + caminho));
    }

    private Long idPorDescricao(String inicio) {
        return transacaoRepository.findAll().stream()
                .filter(t -> t.getDescricaoOriginal().startsWith(inicio))
                .findFirst().orElseThrow().getId();
    }

    @Nested
    @DisplayName("resumo (RF21, RF26)")
    class Resumo {

        @Test
        @DisplayName("receitas, despesas (positivas) e saldo, como string")
        void tudo() throws Exception {
            relatorio("/resumo")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.receitas").value("3589.90"))
                    .andExpect(jsonPath("$.despesas").value("282.75"))
                    .andExpect(jsonPath("$.saldo").value("3307.15"));
        }

        @Test
        @DisplayName("transferência interna fica fora das somas (RF14)")
        void semTransferenciaInterna() throws Exception {
            transacaoService.enriquecer(idPorDescricao("SALÁRIO"), null, null, true);

            relatorio("/resumo")
                    .andExpect(jsonPath("$.receitas").value("89.90"))
                    .andExpect(jsonPath("$.saldo").value("-192.85"));
        }

        @Test
        @DisplayName("filtros por conta, período e categoria (com subcategorias)")
        void filtros() throws Exception {
            relatorio("/resumo?contaId=" + nubank)
                    .andExpect(jsonPath("$.receitas").value("89.90"))
                    .andExpect(jsonPath("$.despesas").value("89.90"))
                    .andExpect(jsonPath("$.saldo").value("0.00"));
            relatorio("/resumo?dataInicio=2026-06-01&dataFim=2026-06-02")
                    .andExpect(jsonPath("$.receitas").value("0.00"))
                    .andExpect(jsonPath("$.despesas").value("240.65"));

            Long alimentacao = categorizacaoService.criarCategoria("Alimentação", null).getId();
            Long padaria = categorizacaoService.criarCategoria("Padaria", alimentacao).getId();
            transacaoService.categorizarManualmente(idPorDescricao("PADARIA"), padaria);
            relatorio("/resumo?categoriaId=" + alimentacao).andExpect(jsonPath("$.despesas").value("42.10"));
        }

        @Test
        @DisplayName("período invertido: 400")
        void periodoInvertido() throws Exception {
            relatorio("/resumo?dataInicio=2026-07-01&dataFim=2026-06-01").andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("por categoria: raiz soma subcategorias, mais despesa primeiro, Não categorizado por último (RF22)")
    void porCategoria() throws Exception {
        Long alimentacao = categorizacaoService.criarCategoria("Alimentação", null).getId();
        Long padaria = categorizacaoService.criarCategoria("Padaria", alimentacao).getId();
        Long moradia = categorizacaoService.criarCategoria("Moradia", null).getId();
        Long saude = categorizacaoService.criarCategoria("Saúde", null).getId();
        categorizacaoService.criarRegra(padaria, Operador.CONTAINS, "padaria", 10);
        categorizacaoService.criarRegra(moradia, Operador.CONTAINS, "agua", 10);
        categorizacaoService.criarRegra(saude, Operador.CONTAINS, "farmacia", 10);
        transacaoService.reaplicarRegras();

        relatorio("/por-categoria")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nome", contains("Moradia", "Saúde", "Alimentação", "Não categorizado")))
                .andExpect(jsonPath("$[0].despesas").value("150.75"))
                .andExpect(jsonPath("$[1].despesas").value("89.90"))
                .andExpect(jsonPath("$[1].receitas").value("89.90")) // o estorno aparece na mesma categoria
                .andExpect(jsonPath("$[1].quantidade").value(2))
                .andExpect(jsonPath("$[2].despesas").value("42.10"))
                .andExpect(jsonPath("$[2].subcategorias[0].nome").value("Padaria"))
                .andExpect(jsonPath("$[3].categoriaId").doesNotExist())
                .andExpect(jsonPath("$[3].receitas").value("3500.00"));
    }

    @Nested
    @DisplayName("evolução mensal e comparativo (RF23, RF24)")
    class Mensal {

        @BeforeEach
        void transacaoEmJulho() {
            transactionTemplate.executeWithoutResult(status -> {
                Conta conta = contaRepository.findById(itau).orElseThrow();
                transacaoService.salvarNovas(conta, List.of(new Transacao(conta, "JUL-1",
                        LocalDate.of(2026, 7, 10), new BigDecimal("-10.00"), "PADARIA JULHO")));
            });
        }

        @Test
        @DisplayName("um item por mês, inclusive meses sem movimento")
        void mensal() throws Exception {
            relatorio("/mensal?mesInicio=2026-05&mesFim=2026-08")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].mes", contains("2026-05", "2026-06", "2026-07", "2026-08")))
                    .andExpect(jsonPath("$[0].saldo").value("0.00"))
                    .andExpect(jsonPath("$[1].receitas").value("3589.90"))
                    .andExpect(jsonPath("$[1].despesas").value("282.75"))
                    .andExpect(jsonPath("$[2].despesas").value("10.00"))
                    .andExpect(jsonPath("$[3].despesas").value("0.00"));
        }

        @Test
        @DisplayName("mensal: ordem invertida ou mais de 36 meses → 400")
        void mensalInvalido() throws Exception {
            relatorio("/mensal?mesInicio=2026-07&mesFim=2026-06").andExpect(status().isBadRequest());
            relatorio("/mensal?mesInicio=2023-01&mesFim=2026-06").andExpect(status().isBadRequest());
            relatorio("/mensal?mesInicio=2026-13&mesFim=2026-06").andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("comparativo: meses em ordem, com totais e categorias de cada um")
        void comparativo() throws Exception {
            relatorio("/comparativo?meses=2026-07,2026-06")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].mes", contains("2026-06", "2026-07")))
                    .andExpect(jsonPath("$[0].despesas").value("282.75"))
                    .andExpect(jsonPath("$[1].despesas").value("10.00"))
                    .andExpect(jsonPath("$[1].porCategoria[0].nome").value("Não categorizado"));
        }

        @Test
        @DisplayName("comparativo com um mês só: 400")
        void comparativoUmMes() throws Exception {
            relatorio("/comparativo?meses=2026-06,2026-06")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("informe de 2 a 12 meses diferentes para comparar"));
        }
    }

    @Nested
    @DisplayName("saldos (RF25, D-10)")
    class Saldos {

        @Test
        @DisplayName("saldo por conta ativa e consolidado; conta sem importação sem saldo")
        void saldos() throws Exception {
            Long semImportacao = contaService.criar(Instituicao.MERCADOPAGO, TipoConta.CORRENTE, "Mercado Pago").getId();
            Long inativa = contaService.criar(Instituicao.ITAU, TipoConta.POUPANCA, "Poupança antiga").getId();
            contaService.inativar(inativa);

            relatorio("/saldos")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.consolidado").value("3572.59")) // 4807.15 + (-1234.56)
                    .andExpect(jsonPath("$.contas.length()").value(3))     // a inativa não entra
                    .andExpect(jsonPath("$.contas[?(@.contaId == " + itau + ")].saldo").value("4807.15"))
                    .andExpect(jsonPath("$.contas[?(@.contaId == " + itau + ")].dataSaldo").value("2026-06-30"))
                    .andExpect(jsonPath("$.contas[?(@.contaId == " + nubank + ")].saldo").value("-1234.56"))
                    .andExpect(jsonPath("$.contas[?(@.contaId == " + semImportacao + ")].saldo").value(
                            org.hamcrest.Matchers.contains((Object) null)));
        }

        @Test
        @DisplayName("importar um extrato mais antigo depois não faz o saldo voltar no tempo")
        void extratoAntigoNaoSobrescreve() throws Exception {
            String antigo = new String(fixture("itau-corrente-v1-cp1252.ofx"), CP1252)
                    .replace("<BALAMT>4807.15", "<BALAMT>1000.00")
                    .replace("<DTASOF>20260630", "<DTASOF>20260531");
            importService.importar(itau, "itau-maio.ofx", antigo.getBytes(CP1252));

            relatorio("/saldos")
                    .andExpect(jsonPath("$.contas[?(@.contaId == " + itau + ")].saldo").value("4807.15"));
        }
    }
}
