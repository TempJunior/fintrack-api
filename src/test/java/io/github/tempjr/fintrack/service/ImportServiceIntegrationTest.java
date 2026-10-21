package io.github.tempjr.fintrack.service;

import io.github.tempjr.fintrack.exception.ArquivoInvalidoException;
import io.github.tempjr.fintrack.exception.ImportacaoRejeitadaException;
import io.github.tempjr.fintrack.model.Conta;
import io.github.tempjr.fintrack.model.Importacao;
import io.github.tempjr.fintrack.model.enums.Instituicao;
import io.github.tempjr.fintrack.model.enums.ResultadoImportacao;
import io.github.tempjr.fintrack.model.enums.StatementFormat;
import io.github.tempjr.fintrack.model.enums.TipoConta;
import io.github.tempjr.fintrack.repository.ContaRepository;
import io.github.tempjr.fintrack.repository.TransacaoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fluxo B de ponta a ponta contra o banco (H2 + Flyway).
 * Sem @Transactional no teste de propósito: queremos ver commit, rollback e a auditoria da falha
 * como acontecem de verdade. Por isso as tabelas são limpas antes de cada teste.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("ImportService (integração)")
class ImportServiceIntegrationTest {

    private static final Charset CP1252 = Charset.forName("windows-1252");
    private static final String ITAU = "itau-corrente-v1-cp1252.ofx";
    private static final String NUBANK_CARTAO = "nubank-cartao-v2-utf8.ofx";

    @Autowired
    private ImportService importService;
    @Autowired
    private ContaService contaService;
    @Autowired
    private ContaRepository contaRepository;
    @Autowired
    private TransacaoRepository transacaoRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private Long itauCorrente;

    @BeforeEach
    void prepararBanco() {
        limparBanco();
        itauCorrente = contaService.criar(Instituicao.ITAU, TipoConta.CORRENTE, "Itaú corrente").getId();
    }

    /** Este teste faz commit de verdade; limpar no fim evita sujar o banco das outras classes de teste. */
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
        try (InputStream in = ImportServiceIntegrationTest.class.getResourceAsStream("/ofx/" + nome)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Variação do arquivo do Itaú com um trecho trocado (ex.: outra conta, valor inválido). */
    private static byte[] itauCom(String de, String para) {
        String texto = new String(fixture(ITAU), CP1252);
        assertThat(texto).contains(de);
        return texto.replace(de, para).getBytes(CP1252);
    }

    private Importacao ultimaImportacao(Long contaId) {
        return importService.listar(contaId).get(0);
    }

    @Nested
    @DisplayName("com arquivo válido")
    class ArquivoValido {

        @Test
        @DisplayName("grava as transações e o resumo com saldo declarado (RF08, D-10)")
        void importa() {
            Importacao importacao = importService.importar(itauCorrente, "extrato-junho.ofx", fixture(ITAU));

            assertThat(importacao.getResultado()).isEqualTo(ResultadoImportacao.SUCESSO);
            assertThat(importacao.getFormato()).isEqualTo(StatementFormat.OFX_V1);
            assertThat(importacao.getTotalLido()).isEqualTo(3);
            assertThat(importacao.getNovas()).isEqualTo(3);
            assertThat(importacao.getDuplicadas()).isZero();
            assertThat(importacao.getSaldoDeclarado()).isEqualByComparingTo("4807.15");
            assertThat(importacao.getDataSaldo()).isEqualTo(LocalDate.of(2026, 6, 30));
            assertThat(transacaoRepository.count()).isEqualTo(3);
        }

        @Test
        @DisplayName("vincula o número da conta na primeira importação (D-09)")
        void vinculaNumero() {
            importService.importar(itauCorrente, "a.ofx", fixture(ITAU));

            assertThat(contaRepository.findById(itauCorrente).orElseThrow().getNumeroExterno())
                    .isEqualTo("1111122222");
        }

        @Test
        @DisplayName("reimportar o mesmo arquivo não duplica (RF07, RNF04)")
        void reimporta() {
            importService.importar(itauCorrente, "a.ofx", fixture(ITAU));

            Importacao segunda = importService.importar(itauCorrente, "a.ofx", fixture(ITAU));

            assertThat(segunda.getNovas()).isZero();
            assertThat(segunda.getDuplicadas()).isEqualTo(3);
            assertThat(transacaoRepository.count()).isEqualTo(3);
        }

        @Test
        @DisplayName("importa extrato de cartão v2 numa conta de cartão")
        void cartao() {
            Long cartao = contaService.criar(Instituicao.NUBANK, TipoConta.CARTAO, "Nubank cartão").getId();

            Importacao importacao = importService.importar(cartao, "nubank.ofx", fixture(NUBANK_CARTAO));

            assertThat(importacao.getFormato()).isEqualTo(StatementFormat.OFX_V2);
            assertThat(importacao.getNovas()).isEqualTo(2);
        }

        @Test
        @DisplayName("guarda só o nome do arquivo, sem o caminho")
        void nomeSemCaminho() {
            Importacao importacao = importService.importar(itauCorrente, "C:\\Users\\fulano\\extrato.ofx", fixture(ITAU));

            assertThat(importacao.getNomeArquivo()).isEqualTo("extrato.ofx");
        }
    }

    @Nested
    @DisplayName("com arquivo rejeitado: nada é gravado e a falha fica no histórico (RF09, RNF05, RNF12)")
    class ArquivoRejeitado {

        private void assertRejeitadoSemGravar(Long contaId, byte[] conteudo, String mensagem) {
            assertThatThrownBy(() -> importService.importar(contaId, "x.ofx", conteudo))
                    .isInstanceOf(ImportacaoRejeitadaException.class)
                    .hasMessageContaining(mensagem);

            assertThat(transacaoRepository.count()).isZero();
            Importacao falha = ultimaImportacao(contaId);
            assertThat(falha.getResultado()).isEqualTo(ResultadoImportacao.FALHA);
            assertThat(falha.getErros()).isEqualTo(1);
            assertThat(falha.getNovas()).isZero();
        }

        @Test
        @DisplayName("arquivo que não é OFX")
        void naoOfx() {
            assertRejeitadoSemGravar(itauCorrente, fixture("nao-e-ofx.pdf"), "Formato de arquivo não reconhecido");
            assertThat(ultimaImportacao(itauCorrente).getFormato()).isNull();
        }

        @Test
        @DisplayName("OFX corrompido")
        void corrompido() {
            assertRejeitadoSemGravar(itauCorrente, fixture("corrompido.ofx"), "inválido ou corrompido");
            assertThat(ultimaImportacao(itauCorrente).getFormato()).isEqualTo(StatementFormat.OFX_V1);
        }

        @Test
        @DisplayName("extrato de cartão numa conta corrente")
        void cartaoEmCorrente() {
            assertRejeitadoSemGravar(itauCorrente, fixture(NUBANK_CARTAO), "extrato de cartão");
        }

        @Test
        @DisplayName("extrato de conta numa conta de cartão")
        void correnteEmCartao() {
            Long cartao = contaService.criar(Instituicao.ITAU, TipoConta.CARTAO, "Itaú cartão").getId();

            assertRejeitadoSemGravar(cartao, fixture(ITAU), "conta escolhida é de cartão");
        }

        @Test
        @DisplayName("arquivo de outro banco")
        void outroBanco() {
            Long nubank = contaService.criar(Instituicao.NUBANK, TipoConta.CORRENTE, "Nubank").getId();

            assertRejeitadoSemGravar(nubank, fixture(ITAU), "outro banco");
        }

        @Test
        @DisplayName("conta inativa")
        void contaInativa() {
            contaService.inativar(itauCorrente);

            assertRejeitadoSemGravar(itauCorrente, fixture(ITAU), "inativa");
        }

        @Test
        @DisplayName("arquivo de outra conta do mesmo banco, depois do vínculo (D-09)")
        void outraConta() {
            importService.importar(itauCorrente, "a.ofx", fixture(ITAU));
            long antes = transacaoRepository.count();

            assertThatThrownBy(() -> importService.importar(itauCorrente, "b.ofx",
                    itauCom("<ACCTID>1111122222", "<ACCTID>9999988888")))
                    .isInstanceOf(ImportacaoRejeitadaException.class)
                    .hasMessageContaining("outra conta")
                    .hasMessageNotContaining("9999988888"); // RNF08

            assertThat(transacaoRepository.count()).isEqualTo(antes);
        }
    }

    @Test
    @DisplayName("atomicidade: transação inválida no meio desfaz até o vínculo da conta (RNF05)")
    void atomicidade() {
        byte[] comValorInvalido = itauCom("<TRNAMT>3500.00", "<TRNAMT>3500.001");

        assertThatThrownBy(() -> importService.importar(itauCorrente, "x.ofx", comValorInvalido))
                .isInstanceOf(ArquivoInvalidoException.class)
                .hasMessageContaining("Transação nº 2");

        Conta conta = contaRepository.findById(itauCorrente).orElseThrow();
        assertThat(conta.getNumeroExterno()).as("vínculo desfeito no rollback").isNull();
        assertThat(transacaoRepository.count()).isZero();
        assertThat(ultimaImportacao(itauCorrente).getResultado()).isEqualTo(ResultadoImportacao.FALHA);
    }

    @Test
    @DisplayName("histórico traz sucesso e falha, mais recente primeiro")
    void historico() {
        importService.importar(itauCorrente, "a.ofx", fixture(ITAU));
        assertThatThrownBy(() -> importService.importar(itauCorrente, "b.pdf", fixture("nao-e-ofx.pdf")));

        assertThat(importService.listar(itauCorrente))
                .extracting(Importacao::getNomeArquivo, Importacao::getResultado)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("b.pdf", ResultadoImportacao.FALHA),
                        org.assertj.core.groups.Tuple.tuple("a.ofx", ResultadoImportacao.SUCESSO));
    }
}
