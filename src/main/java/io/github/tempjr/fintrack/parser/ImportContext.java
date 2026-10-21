package io.github.tempjr.fintrack.parser;

import io.github.tempjr.fintrack.model.enums.Instituicao;
import io.github.tempjr.fintrack.model.enums.StatementFormat;

/**
 * O que se sabe no momento do upload (arquitetura §7.1). Serve para o resolver escolher o parser.
 * O charset não está aqui: no OFX ele vem no cabeçalho do arquivo e a ofx4j o aplica (ADR-004).
 */
public record ImportContext(Instituicao instituicao, StatementFormat formato, String nomeArquivo) {
}
