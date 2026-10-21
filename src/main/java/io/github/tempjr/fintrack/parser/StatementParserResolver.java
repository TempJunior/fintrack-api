package io.github.tempjr.fintrack.parser;

import io.github.tempjr.fintrack.exception.ArquivoInvalidoException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Escolhe o parser (Factory/Registry — ADR-002). O Spring injeta a lista de todos os
 * {@link StatementParser} registrados; adicionar um formato não exige mudar esta classe.
 */
@Component
public class StatementParserResolver {

    private final List<StatementParser> parsers;

    public StatementParserResolver(List<StatementParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    public StatementParser resolver(ImportContext ctx) {
        return parsers.stream()
                .filter(parser -> parser.supports(ctx))
                .findFirst()
                .orElseThrow(() -> new ArquivoInvalidoException("Formato de arquivo não suportado: " + ctx.formato()));
    }
}
