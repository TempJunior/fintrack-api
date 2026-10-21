package io.github.tempjr.fintrack.parser;

import io.github.tempjr.fintrack.exception.ArquivoInvalidoException;
import io.github.tempjr.fintrack.model.enums.StatementFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FormatDetector")
class FormatDetectorTest {

    private final FormatDetector detector = new FormatDetector();

    private static byte[] fixture(String nome) throws IOException {
        try (var in = OfxStatementParserTest.arquivo(nome)) {
            return in.readAllBytes();
        }
    }

    @Test
    @DisplayName("cabeçalho OFXHEADER: é OFX v1")
    void ofxV1() throws IOException {
        assertThat(detector.detectar(fixture("itau-corrente-v1-cp1252.ofx"))).isEqualTo(StatementFormat.OFX_V1);
    }

    @Test
    @DisplayName("XML com <?OFX ...?> é OFX v2")
    void ofxV2() throws IOException {
        assertThat(detector.detectar(fixture("nubank-cartao-v2-utf8.ofx"))).isEqualTo(StatementFormat.OFX_V2);
    }

    @Test
    @DisplayName("ignora BOM do UTF-8 e espaços antes do cabeçalho")
    void bomEEspacos() {
        byte[] conteudo = "﻿\r\n  OFXHEADER:100\r\n".getBytes(StandardCharsets.UTF_8);

        assertThat(detector.detectar(conteudo)).isEqualTo(StatementFormat.OFX_V1);
    }

    @ParameterizedTest(name = "rejeita {0}")
    @ValueSource(strings = {"nao-e-ofx.pdf", "nao-e-ofx.csv"})
    @DisplayName("rejeita o que não é OFX, mesmo que o app tenha deixado passar")
    void naoOfx(String nome) throws IOException {
        byte[] conteudo = fixture(nome);

        assertThatThrownBy(() -> detector.detectar(conteudo))
                .isInstanceOf(ArquivoInvalidoException.class)
                .hasMessageContaining("Formato de arquivo não reconhecido");
    }

    @Test
    @DisplayName("XML que não é OFX é rejeitado")
    void xmlQualquer() {
        byte[] conteudo = "<?xml version=\"1.0\"?><nota/>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> detector.detectar(conteudo)).isInstanceOf(ArquivoInvalidoException.class);
    }

    @Test
    @DisplayName("arquivo vazio é rejeitado")
    void vazio() {
        assertThatThrownBy(() -> detector.detectar(new byte[0]))
                .isInstanceOf(ArquivoInvalidoException.class)
                .hasMessage("O arquivo está vazio.");
    }
}
