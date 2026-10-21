package io.github.tempjr.fintrack.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DedupKey (ADR-005)")
class DedupKeyTest {

    private static final LocalDate DATA = LocalDate.of(2026, 6, 10);

    @Test
    @DisplayName("com FITID, a chave é o FITID sem espaços nas pontas")
    void comFitId() {
        assertThat(DedupKey.gerar(" 20260610001 ", DATA, new BigDecimal("-10.00"), "PIX"))
                .isEqualTo("20260610001");
    }

    @Test
    @DisplayName("aceita FITID não numérico, como o UUID do Nubank")
    void fitIdNaoNumerico() {
        String uuid = "5f8a1c2e-9b3d-4e7a-8c1f-2d3e4f5a6b7c";

        assertThat(DedupKey.gerar(uuid, DATA, new BigDecimal("-10.00"), "PIX")).isEqualTo(uuid);
    }

    @Test
    @DisplayName("sem FITID, mesma transação gera a mesma chave (idempotência)")
    void hashEstavel() {
        String primeira = DedupKey.gerar(null, DATA, new BigDecimal("-10.00"), "PIX ENVIADO");
        String segunda = DedupKey.gerar(null, DATA, new BigDecimal("-10.0"), "  pix   enviado ");

        assertThat(primeira).isEqualTo(segunda).startsWith("H:").hasSize(2 + 64);
    }

    @Test
    @DisplayName("sem FITID, valor diferente gera chave diferente")
    void hashMudaComValor() {
        String a = DedupKey.gerar(null, DATA, new BigDecimal("-10.00"), "PIX");
        String b = DedupKey.gerar(null, DATA, new BigDecimal("-10.01"), "PIX");

        assertThat(a).isNotEqualTo(b);
    }
}
