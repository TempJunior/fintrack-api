package io.github.tempjr.fintrack.model;

import io.github.tempjr.fintrack.model.enums.Operador;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Regras de categorização")
class RegraCategorizacaoTest {

    private final Categoria alimentacao = new Categoria("Alimentação");
    private final Categoria transporte = new Categoria("Transporte");

    private RegraCategorizacao regra(Operador operador, String padrao) {
        return new RegraCategorizacao(alimentacao, operador, padrao, 10);
    }

    @ParameterizedTest(name = "{0} \"{1}\" em \"{2}\" → {3}")
    @CsvSource(delimiter = ';', value = {
            "CONTAINS    ; padaria          ; PADARIA SÃO JOÃO – CAFÉ ; true",
            "CONTAINS    ; sao joao         ; PADARIA SÃO JOÃO        ; true",   // sem acento casa com acento
            "CONTAINS    ; São   João       ; padaria sao joao        ; true",   // acento e espaços no padrão
            "CONTAINS    ; mercado          ; PADARIA SÃO JOÃO        ; false",
            "STARTS_WITH ; uber             ; UBER *TRIP              ; true",
            "STARTS_WITH ; trip             ; UBER *TRIP              ; false",
            "REGEX       ; ^PIX (ENVIADO|RECEBIDO) ; pix enviado fulano ; true",   // regex ignora maiúsculas
            "REGEX       ; \\d{4}$          ; COMPRA CARTAO 1234      ; true",
    })
    void corresponde(Operador operador, String padrao, String descricao, boolean esperado) {
        assertThat(regra(operador, padrao).corresponde(descricao)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("descrição vazia não casa com nada")
    void descricaoVazia() {
        assertThat(regra(Operador.CONTAINS, "x").corresponde("  ")).isFalse();
        assertThat(regra(Operador.CONTAINS, "x").corresponde(null)).isFalse();
    }

    @Test
    @DisplayName("regex inválida é rejeitada já na criação")
    void regexInvalida() {
        assertThatThrownBy(() -> regra(Operador.REGEX, "PIX ("))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("padrão não é uma expressão regular válida");
    }

    @Test
    @DisplayName("prioridade negativa é rejeitada")
    void prioridadeNegativa() {
        assertThatThrownBy(() -> new RegraCategorizacao(alimentacao, Operador.CONTAINS, "x", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("categorizador: a regra de menor prioridade vence")
    void prioridade() {
        RegraCategorizacao generica = new RegraCategorizacao(alimentacao, Operador.CONTAINS, "uber", 50);
        RegraCategorizacao especifica = new RegraCategorizacao(transporte, Operador.CONTAINS, "uber trip", 10);

        CategorizadorPorRegras categorizador = new CategorizadorPorRegras(List.of(generica, especifica));

        assertThat(categorizador.categorizar("UBER TRIP 123")).containsSame(transporte);
        assertThat(categorizador.categorizar("UBER EATS")).containsSame(alimentacao);
        assertThat(categorizador.categorizar("PADARIA")).isEmpty();
    }

    @Test
    @DisplayName("categorizador: no empate de prioridade, a regra criada antes vence")
    void empate() {
        RegraCategorizacao primeira = new RegraCategorizacao(alimentacao, Operador.CONTAINS, "uber", 10);
        RegraCategorizacao segunda = new RegraCategorizacao(transporte, Operador.CONTAINS, "uber", 10);
        ReflectionTestUtils.setField(primeira, "id", 1L);
        ReflectionTestUtils.setField(segunda, "id", 2L);

        CategorizadorPorRegras categorizador = new CategorizadorPorRegras(List.of(segunda, primeira));

        assertThat(categorizador.categorizar("UBER")).containsSame(alimentacao);
    }

    @Test
    @DisplayName("categoria: no máximo 2 níveis")
    void doisNiveis() {
        Categoria restaurante = new Categoria("Restaurante", alimentacao);

        assertThat(restaurante.isSubcategoria()).isTrue();
        assertThatThrownBy(() -> new Categoria("Japonês", restaurante))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 níveis");
    }
}
