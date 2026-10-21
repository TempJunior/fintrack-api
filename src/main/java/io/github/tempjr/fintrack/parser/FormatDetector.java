package io.github.tempjr.fintrack.parser;

import io.github.tempjr.fintrack.exception.ArquivoInvalidoException;
import io.github.tempjr.fintrack.model.enums.StatementFormat;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Descobre o formato pelos primeiros bytes (arquitetura §7.3). O app filtra o seletor de arquivos,
 * mas o backend não confia nisso: PDF, CSV, imagem etc. são rejeitados aqui (RF05, RF09).
 */
@Component
public class FormatDetector {

    private static final int BYTES_INSPECIONADOS = 1024;
    /** BOM do UTF-8 (EF BB BF) lido como ISO-8859-1. */
    private static final String BOM_UTF8 = "ï»¿";

    public StatementFormat detectar(byte[] conteudo) {
        if (conteudo == null || conteudo.length == 0) {
            throw new ArquivoInvalidoException("O arquivo está vazio.");
        }
        // ISO-8859-1 mapeia cada byte para um caractere: serve para olhar o cabeçalho sem saber o encoding.
        String inicio = new String(conteudo, 0, Math.min(conteudo.length, BYTES_INSPECIONADOS),
                StandardCharsets.ISO_8859_1);
        if (inicio.startsWith(BOM_UTF8)) {
            inicio = inicio.substring(BOM_UTF8.length());
        }
        inicio = inicio.stripLeading();

        if (inicio.startsWith("OFXHEADER:")) {
            return StatementFormat.OFX_V1; // cabeçalho SGML (OFX 1.x)
        }
        if (inicio.startsWith("<?xml") && inicio.contains("<?OFX")) {
            return StatementFormat.OFX_V2; // XML com instrução de processamento OFX (OFX 2.x)
        }
        throw new ArquivoInvalidoException("Formato de arquivo não reconhecido. Envie um extrato OFX exportado pelo banco.");
    }
}
