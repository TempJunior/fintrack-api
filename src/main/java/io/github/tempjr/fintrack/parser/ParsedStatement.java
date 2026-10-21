package io.github.tempjr.fintrack.parser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resultado do parse de um arquivo: o modelo canônico (arquitetura §4.1).
 *
 * @param ledgerBalance LEDGERBAL/BALAMT: saldo da conta em {@code dataSaldo} (D-10); pode ser nulo
 * @param dataSaldo     LEDGERBAL/DTASOF
 */
public record ParsedStatement(
        AccountRef declaredAccount,
        LocalDate inicio,
        LocalDate fim,
        BigDecimal ledgerBalance,
        LocalDate dataSaldo,
        List<RawTransaction> transactions
) {

    public ParsedStatement {
        transactions = List.copyOf(transactions);
    }

    /** Sem conta, saldo nem transações (RNF08). */
    @Override
    public String toString() {
        return "ParsedStatement{inicio=" + inicio + ", fim=" + fim + ", transacoes=" + transactions.size() + "}";
    }
}
