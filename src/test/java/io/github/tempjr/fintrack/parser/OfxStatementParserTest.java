package io.github.tempjr.fintrack.parser;

import io.github.tempjr.fintrack.exception.ArquivoInvalidoException;
import io.github.tempjr.fintrack.model.enums.Instituicao;
import io.github.tempjr.fintrack.model.enums.StatementFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Arquivos em src/test/resources/ofx são sintéticos (dados inventados). */
@DisplayName("OfxStatementParser")
class OfxStatementParserTest {

    private final OfxStatementParser parser = new OfxStatementParser();

    static InputStream arquivo(String nome) {
        InputStream in = OfxStatementParserTest.class.getResourceAsStream("/ofx/" + nome);
        if (in == null) {
            throw new UncheckedIOException(new IOException("fixture não encontrada: " + nome));
        }
        return in;
    }

    private ParsedStatement parse(String nome, StatementFormat formato) {
        return parser.parse(arquivo(nome), new ImportContext(Instituicao.ITAU, formato, nome));
    }

    @Nested
    @DisplayName("OFX v1 (SGML) do Itaú em Windows-1252")
    class ItauV1 {

        private final ParsedStatement extrato = parse("itau-corrente-v1-cp1252.ofx", StatementFormat.OFX_V1);

        @Test
        @DisplayName("lê a conta declarada (banco e número) como extrato de conta, não de cartão")
        void contaDeclarada() {
            assertThat(extrato.declaredAccount().bankId()).isEqualTo("0341");
            assertThat(extrato.declaredAccount().acctId()).isEqualTo("1111122222");
            assertThat(extrato.declaredAccount().cartaoDeCredito()).isFalse();
        }

        @Test
        @DisplayName("lê as transações com FITID, data, valor e descrição")
        void transacoes() {
            assertThat(extrato.transactions()).hasSize(3);
            RawTransaction primeira = extrato.transactions().get(0);

            assertThat(primeira.providerTransactionId()).isEqualTo("20260602001");
            assertThat(primeira.date()).isEqualTo(LocalDate.of(2026, 6, 2));
            assertThat(primeira.amount()).isEqualByComparingTo("-150.75");
        }

        @Test
        @DisplayName("decodifica pelo CHARSET:1252 do cabeçalho, inclusive o travessão (ADR-004)")
        void acentos() {
            assertThat(extrato.transactions())
                    .extracting(RawTransaction::description)
                    // "–" é 0x96 no Windows-1252: em ISO-8859-1 viraria um caractere de controle.
                    .containsExactly("PAGTO ÁGUA E ESGOTO", "SALÁRIO EMPRESA FICTÍCIA", "PADARIA SÃO JOÃO – CAFÉ");
        }

        @Test
        @DisplayName("data com fuso [-03:EST] mantém o dia escrito no arquivo")
        void dataComFuso() {
            assertThat(extrato.transactions().get(2).date()).isEqualTo(LocalDate.of(2026, 6, 30));
            assertThat(extrato.inicio()).isEqualTo(LocalDate.of(2026, 6, 1));
            assertThat(extrato.fim()).isEqualTo(LocalDate.of(2026, 6, 30));
        }

        @Test
        @DisplayName("lê o saldo declarado e a data do saldo (D-10)")
        void saldo() {
            assertThat(extrato.ledgerBalance()).isEqualByComparingTo("4807.15");
            assertThat(extrato.dataSaldo()).isEqualTo(LocalDate.of(2026, 6, 30));
        }
    }

    @Nested
    @DisplayName("OFX v2 (XML) de cartão, estilo Nubank")
    class NubankCartaoV2 {

        private final ParsedStatement extrato = parse("nubank-cartao-v2-utf8.ofx", StatementFormat.OFX_V2);

        @Test
        @DisplayName("reconhece extrato de cartão, sem BANKID")
        void cartao() {
            assertThat(extrato.declaredAccount().cartaoDeCredito()).isTrue();
            assertThat(extrato.declaredAccount().bankId()).isNull();
            assertThat(extrato.declaredAccount().acctId()).isEqualTo("cartao-ficticio-0001");
        }

        @Test
        @DisplayName("aceita FITID em formato UUID (estado §7, item 1)")
        void fitIdUuid() {
            assertThat(extrato.transactions().get(0).providerTransactionId())
                    .isEqualTo("5f8a1c2e-9b3d-4e7a-8c1f-2d3e4f5a6b7c");
        }

        @Test
        @DisplayName("data sem fuso não volta um dia")
        void dataSemFuso() {
            assertThat(extrato.transactions().get(0).date()).isEqualTo(LocalDate.of(2026, 6, 1));
        }

        @Test
        @DisplayName("acentos em UTF-8 e saldo negativo do cartão")
        void acentosESaldo() {
            assertThat(extrato.transactions().get(0).description()).isEqualTo("Farmácia Fictícia");
            assertThat(extrato.ledgerBalance()).isEqualByComparingTo("-1234.56");
        }
    }

    @Test
    @DisplayName("arquivo corrompido vira ArquivoInvalidoException com mensagem clara (RF09)")
    void corrompido() {
        assertThatThrownBy(() -> parse("corrompido.ofx", StatementFormat.OFX_V1))
                .isInstanceOf(ArquivoInvalidoException.class)
                .hasMessage("Arquivo OFX inválido ou corrompido.");
    }

    @Test
    @DisplayName("toString do extrato não expõe conta nem valores (RNF08)")
    void toStringSeguro() {
        ParsedStatement extrato = parse("itau-corrente-v1-cp1252.ofx", StatementFormat.OFX_V1);

        assertThat(extrato.toString()).doesNotContain("1111122222").doesNotContain("4807");
        assertThat(extrato.declaredAccount().toString()).doesNotContain("1111122222");
    }
}
