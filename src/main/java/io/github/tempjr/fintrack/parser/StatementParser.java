package io.github.tempjr.fintrack.parser;

import java.io.InputStream;

/**
 * Porta de entrada de um formato de extrato (Strategy — ADR-002). Cada formato/banco implementa esta
 * interface como um {@code @Component}; o {@link StatementParserResolver} escolhe qual usar.
 * Formato novo = classe nova, sem mexer nas existentes.
 */
public interface StatementParser {

    boolean supports(ImportContext ctx);

    /** @throws io.github.tempjr.fintrack.exception.ArquivoInvalidoException se o conteúdo não puder ser lido */
    ParsedStatement parse(InputStream in, ImportContext ctx);
}
