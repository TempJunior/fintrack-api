package io.github.tempjr.fintrack.parser;

import com.webcohesion.ofx4j.io.DefaultStringConversion;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

/**
 * Faz a ofx4j ler só a <b>data literal</b> dos campos de data do OFX, ignorando hora e fuso
 * (arquitetura §4.2: "só a data tem significado").
 * <p>
 * Por quê: a ofx4j converte "20260610100000[-03:EST]" num instante e, quando não há fuso
 * ("20260610"), assume UTC. Nenhum fuso fixo acerta os dois casos na volta para LocalDate
 * (com America/Sao_Paulo, "20260610" vira 09/06; com UTC, 22h de Brasília vira o dia seguinte).
 * Aqui a data é sempre os 8 primeiros dígitos, representada como meia-noite UTC; use
 * {@link #paraLocalDate(Date)} para voltar.
 */
class OfxDataLiteralConversion extends DefaultStringConversion {

    @Override
    protected Date parseDate(String valor) {
        if (valor == null || valor.length() < 8) {
            throw new IllegalArgumentException("data OFX inválida");
        }
        try {
            LocalDate data = LocalDate.parse(valor.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
            return Date.from(data.atStartOfDay(ZoneOffset.UTC).toInstant());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("data OFX inválida", e);
        }
    }

    static LocalDate paraLocalDate(Date data) {
        return data == null ? null : data.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
    }
}
