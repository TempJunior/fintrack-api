package io.github.tempjr.fintrack.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SegurancaProperties")
class SegurancaPropertiesTest {

    @ParameterizedTest(name = "token \"{0}\" impede a subida")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "curto-demais"})
    void tokenAusenteOuCurto(String token) {
        assertThatThrownBy(() -> new SegurancaProperties(token))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fintrack.seguranca.token");
    }

    @Test
    @DisplayName("mensagem de token curto não mostra o valor")
    void mensagemSemValor() {
        assertThatThrownBy(() -> new SegurancaProperties("segredo-curto"))
                .hasMessageNotContaining("segredo-curto");
    }

    @Test
    @DisplayName("toString esconde o token (RNF08)")
    void toStringSemToken() {
        String token = "x".repeat(40);

        assertThat(new SegurancaProperties(token).toString()).doesNotContain(token);
    }
}
