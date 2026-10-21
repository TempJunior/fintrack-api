package io.github.tempjr.fintrack.model;

import io.github.tempjr.fintrack.model.enums.Instituicao;
import io.github.tempjr.fintrack.model.enums.OrigemCategoria;
import io.github.tempjr.fintrack.model.enums.TipoConta;
import io.github.tempjr.fintrack.model.enums.TipoTransacao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Transacao")
class TransacaoTest {

    private final Conta conta = new Conta(Instituicao.ITAU, TipoConta.CORRENTE, "Itaú corrente");
    private final Categoria transporte = new Categoria("Transporte");
    private final Categoria trabalho = new Categoria("Trabalho");

    private Transacao novaTransacao(String valor) {
        return new Transacao(conta, "FIT-1", LocalDate.of(2026, 6, 10), new BigDecimal(valor), "UBER *TRIP");
    }

    @Nested
    @DisplayName("ao ser criada")
    class AoSerCriada {

        @Test
        @DisplayName("valor negativo é débito e positivo é crédito")
        void tipoPeloSinal() {
            assertThat(novaTransacao("-35.90").getTipo()).isEqualTo(TipoTransacao.DEBITO);
            assertThat(novaTransacao("1500.00").getTipo()).isEqualTo(TipoTransacao.CREDITO);
        }

        @Test
        @DisplayName("normaliza o valor para 2 casas sem arredondar")
        void normalizaEscala() {
            assertThat(novaTransacao("10.5").getValor()).isEqualByComparingTo("10.50")
                    .hasToString("10.50");
        }

        @Test
        @DisplayName("rejeita valor com mais de 2 casas decimais")
        void rejeitaTresCasas() {
            assertThatThrownBy(() -> novaTransacao("10.555"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("2 casas");
        }

        @Test
        @DisplayName("usa o FITID como chave de dedup")
        void dedupKeyPeloFitId() {
            assertThat(novaTransacao("-35.90").getDedupKey()).isEqualTo("FIT-1");
        }

        @Test
        @DisplayName("sem FITID, usa o hash como chave de dedup")
        void dedupKeySemFitId() {
            Transacao semFitId = new Transacao(conta, "  ", LocalDate.of(2026, 6, 10),
                    new BigDecimal("-35.90"), "UBER *TRIP");

            assertThat(semFitId.getFitId()).isNull();
            assertThat(semFitId.getDedupKey()).startsWith("H:");
        }

        @Test
        @DisplayName("começa sem categoria (Não categorizado)")
        void semCategoria() {
            Transacao transacao = novaTransacao("-35.90");

            assertThat(transacao.getCategoria()).isNull();
            assertThat(transacao.getOrigemCategoria()).isNull();
            assertThat(transacao.isTransferenciaInterna()).isFalse();
        }
    }

    @Nested
    @DisplayName("ao ser categorizada (RF18, D-11)")
    class AoSerCategorizada {

        @Test
        @DisplayName("regra categoriza transação sem categoria")
        void regraEmTransacaoSemCategoria() {
            Transacao transacao = novaTransacao("-35.90");

            boolean aplicou = transacao.categorizarPorRegra(transporte);

            assertThat(aplicou).isTrue();
            assertThat(transacao.getCategoria()).isSameAs(transporte);
            assertThat(transacao.getOrigemCategoria()).isEqualTo(OrigemCategoria.REGRA);
        }

        @Test
        @DisplayName("regra não sobrescreve escolha manual")
        void regraNaoSobrescreveManual() {
            Transacao transacao = novaTransacao("-35.90");
            transacao.categorizarManualmente(trabalho);

            boolean aplicou = transacao.categorizarPorRegra(transporte);

            assertThat(aplicou).isFalse();
            assertThat(transacao.getCategoria()).isSameAs(trabalho);
            assertThat(transacao.getOrigemCategoria()).isEqualTo(OrigemCategoria.MANUAL);
        }

        @Test
        @DisplayName("escolha manual sobrescreve categoria de regra")
        void manualSobrescreveRegra() {
            Transacao transacao = novaTransacao("-35.90");
            transacao.categorizarPorRegra(transporte);

            transacao.categorizarManualmente(trabalho);

            assertThat(transacao.getCategoria()).isSameAs(trabalho);
            assertThat(transacao.getOrigemCategoria()).isEqualTo(OrigemCategoria.MANUAL);
        }

        @Test
        @DisplayName("remover categoria limpa também a origem")
        void removerCategoria() {
            Transacao transacao = novaTransacao("-35.90");
            transacao.categorizarManualmente(trabalho);

            transacao.removerCategoria();

            assertThat(transacao.getCategoria()).isNull();
            assertThat(transacao.getOrigemCategoria()).isNull();
        }
    }

    @Test
    @DisplayName("tags são normalizadas e não se repetem")
    void tags() {
        Transacao transacao = novaTransacao("-35.90");

        transacao.adicionarTag(" Viagem ");
        transacao.adicionarTag("viagem");

        assertThat(transacao.getTags()).containsExactly("viagem");
    }

    @Test
    @DisplayName("toString não expõe valor nem descrição (RNF08)")
    void toStringSemDadoSensivel() {
        String texto = novaTransacao("-35.90").toString();

        assertThat(texto).doesNotContain("35.90").doesNotContain("UBER");
    }
}
