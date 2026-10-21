package io.github.tempjr.fintrack.parser;

import io.github.tempjr.fintrack.model.enums.StatementFormat;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Descobre o encoding declarado no próprio arquivo OFX (ADR-004).
 * <p>
 * Necessário porque a ofx4j 1.39 lê qualquer arquivo como ISO-8859-1 (padrão global da lib),
 * ignorando o cabeçalho. Isso quebra UTF-8 (OFX v2) e os caracteres 0x80–0x9F do Windows-1252
 * (–, “, ”, €), usado pelos bancos brasileiros.
 * <ul>
 *   <li>OFX v1: {@code ENCODING:UTF-8} → UTF-8; senão {@code CHARSET:1252} → Windows-1252,
 *       {@code CHARSET:ISO-8859-1} → ISO-8859-1; ausente ou NONE → Windows-1252.</li>
 *   <li>OFX v2: {@code encoding="..."} da declaração XML; ausente → UTF-8 (padrão do XML).</li>
 * </ul>
 */
final class OfxCharset {

    static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private static final int BYTES_CABECALHO = 1024;
    private static final Pattern V1_ENCODING = Pattern.compile("(?m)^\\s*ENCODING:\\s*(\\S+)");
    private static final Pattern V1_CHARSET = Pattern.compile("(?m)^\\s*CHARSET:\\s*(\\S+)");
    private static final Pattern V2_ENCODING = Pattern.compile("<\\?xml[^>]*encoding\\s*=\\s*[\"']([^\"']+)[\"']");

    private OfxCharset() {
    }

    static Charset detectar(byte[] conteudo, StatementFormat formato) {
        String cabecalho = new String(conteudo, 0, Math.min(conteudo.length, BYTES_CABECALHO),
                StandardCharsets.ISO_8859_1);
        return formato == StatementFormat.OFX_V2 ? doXml(cabecalho) : doSgml(cabecalho);
    }

    private static Charset doSgml(String cabecalho) {
        String encoding = primeiroGrupo(V1_ENCODING, cabecalho);
        if (encoding != null && encoding.toUpperCase(Locale.ROOT).replace("-", "").equals("UTF8")) {
            return StandardCharsets.UTF_8;
        }
        String charset = primeiroGrupo(V1_CHARSET, cabecalho);
        if (charset == null) {
            return WINDOWS_1252;
        }
        return switch (charset.toUpperCase(Locale.ROOT)) {
            case "ISO-8859-1", "8859-1", "ISO8859-1", "LATIN1" -> StandardCharsets.ISO_8859_1;
            // "1252", "NONE" e valores desconhecidos: Windows-1252 é o que os bancos brasileiros usam
            // e é compatível com ISO-8859-1 nas letras acentuadas.
            default -> WINDOWS_1252;
        };
    }

    private static Charset doXml(String cabecalho) {
        String encoding = primeiroGrupo(V2_ENCODING, cabecalho);
        if (encoding == null) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (IllegalArgumentException e) { // nome inválido ou não suportado
            return StandardCharsets.UTF_8;
        }
    }

    private static String primeiroGrupo(Pattern padrao, String texto) {
        Matcher m = padrao.matcher(texto);
        return m.find() ? m.group(1).strip() : null;
    }
}
