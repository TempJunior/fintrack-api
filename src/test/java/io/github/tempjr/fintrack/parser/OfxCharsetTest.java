package io.github.tempjr.fintrack.parser;

import io.github.tempjr.fintrack.model.enums.StatementFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OfxCharset (ADR-004)")
class OfxCharsetTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    @DisplayName("v1 com CHARSET:1252 → Windows-1252")
    void v1Cp1252() {
        assertThat(OfxCharset.detectar(bytes("OFXHEADER:100\r\nENCODING:USASCII\r\nCHARSET:1252\r\n"), StatementFormat.OFX_V1))
                .isEqualTo(OfxCharset.WINDOWS_1252);
    }

    @Test
    @DisplayName("v1 com CHARSET:ISO-8859-1 → ISO-8859-1")
    void v1Latin1() {
        assertThat(OfxCharset.detectar(bytes("OFXHEADER:100\nCHARSET:ISO-8859-1\n"), StatementFormat.OFX_V1))
                .isEqualTo(StandardCharsets.ISO_8859_1);
    }

    @Test
    @DisplayName("v1 com ENCODING:UTF-8 → UTF-8, mesmo com CHARSET:NONE")
    void v1Utf8() {
        assertThat(OfxCharset.detectar(bytes("OFXHEADER:100\nENCODING:UTF-8\nCHARSET:NONE\n"), StatementFormat.OFX_V1))
                .isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("v1 sem CHARSET → Windows-1252 (padrão dos bancos brasileiros)")
    void v1SemCharset() {
        assertThat(OfxCharset.detectar(bytes("OFXHEADER:100\n"), StatementFormat.OFX_V1))
                .isEqualTo(OfxCharset.WINDOWS_1252);
    }

    @Test
    @DisplayName("v2 usa o encoding da declaração XML; sem ela, UTF-8")
    void v2() {
        assertThat(OfxCharset.detectar(bytes("<?xml version=\"1.0\" encoding='ISO-8859-1'?>"), StatementFormat.OFX_V2))
                .isEqualTo(StandardCharsets.ISO_8859_1);
        assertThat(OfxCharset.detectar(bytes("<?xml version=\"1.0\"?>"), StatementFormat.OFX_V2))
                .isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("v2 com encoding inexistente cai para UTF-8 em vez de quebrar")
    void v2EncodingInvalido() {
        assertThat(OfxCharset.detectar(bytes("<?xml version=\"1.0\" encoding=\"nao-existe\"?>"), StatementFormat.OFX_V2))
                .isEqualTo(StandardCharsets.UTF_8);
    }
}
