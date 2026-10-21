package io.github.tempjr.fintrack.parser;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma transação do arquivo, já normalizada (arquitetura §4.2). Ainda não é entidade: quem decide
 * gravar é o ImportService.
 *
 * @param providerTransactionId FITID; pode ser nulo
 * @param amount                negativo = débito
 */
public record RawTransaction(String providerTransactionId, LocalDate date, BigDecimal amount, String description) {

    /** Sem valor nem descrição (RNF08). */
    @Override
    public String toString() {
        return "RawTransaction{date=" + date + "}";
    }
}
