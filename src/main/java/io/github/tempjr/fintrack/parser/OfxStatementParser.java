package io.github.tempjr.fintrack.parser;

import com.webcohesion.ofx4j.domain.data.MessageSetType;
import com.webcohesion.ofx4j.domain.data.ResponseEnvelope;
import com.webcohesion.ofx4j.domain.data.banking.BankAccountDetails;
import com.webcohesion.ofx4j.domain.data.banking.BankStatementResponseTransaction;
import com.webcohesion.ofx4j.domain.data.banking.BankingResponseMessageSet;
import com.webcohesion.ofx4j.domain.data.common.BalanceInfo;
import com.webcohesion.ofx4j.domain.data.common.StatementResponse;
import com.webcohesion.ofx4j.domain.data.common.Transaction;
import com.webcohesion.ofx4j.domain.data.common.TransactionList;
import com.webcohesion.ofx4j.domain.data.creditcard.CreditCardResponseMessageSet;
import com.webcohesion.ofx4j.domain.data.creditcard.CreditCardStatementResponseTransaction;
import com.webcohesion.ofx4j.io.AggregateUnmarshaller;
import io.github.tempjr.fintrack.exception.ArquivoInvalidoException;
import io.github.tempjr.fintrack.model.enums.StatementFormat;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser OFX (v1 SGML e v2 XML) sobre a ofx4j. Serve qualquer banco que exporte OFX (ADR-002).
 * Lê extrato de conta (BANKMSGSRSV1) e de cartão (CREDITCARDMSGSRSV1); um extrato por arquivo.
 * O encoding declarado no arquivo (ex.: CHARSET:1252) é aplicado por {@link OfxCharset} (ADR-004).
 */
@Component
public class OfxStatementParser implements StatementParser {

    @Override
    public boolean supports(ImportContext ctx) {
        return ctx.formato() == StatementFormat.OFX_V1 || ctx.formato() == StatementFormat.OFX_V2;
    }

    @Override
    public ParsedStatement parse(InputStream in, ImportContext ctx) {
        ResponseEnvelope envelope = ler(in, ctx.formato());

        List<StatementResponse> extratos = new ArrayList<>();
        List<AccountRef> contas = new ArrayList<>();
        coletarExtratosDeConta(envelope, extratos, contas);
        coletarExtratosDeCartao(envelope, extratos, contas);

        if (extratos.isEmpty()) {
            throw new ArquivoInvalidoException("O arquivo não contém extrato de conta nem de cartão.");
        }
        if (extratos.size() > 1) {
            throw new ArquivoInvalidoException("O arquivo contém mais de uma conta. Exporte um extrato por conta.");
        }
        return montar(extratos.get(0), contas.get(0));
    }

    private static ResponseEnvelope ler(InputStream in, StatementFormat formato) {
        AggregateUnmarshaller<ResponseEnvelope> unmarshaller = new AggregateUnmarshaller<>(ResponseEnvelope.class);
        unmarshaller.setConversion(new OfxDataLiteralConversion());
        try {
            byte[] conteudo = in.readAllBytes();
            // Decodifica com o encoding declarado no arquivo; a ofx4j sozinha usaria ISO-8859-1 (ADR-004).
            Charset charset = OfxCharset.detectar(conteudo, formato);
            return unmarshaller.unmarshal(new InputStreamReader(new ByteArrayInputStream(conteudo), charset));
        } catch (Exception e) {
            // Mensagem da lib não vai para o usuário (pode trazer trechos do arquivo); fica só na causa.
            throw new ArquivoInvalidoException("Arquivo OFX inválido ou corrompido.", e);
        }
    }

    private static void coletarExtratosDeConta(ResponseEnvelope envelope, List<StatementResponse> extratos,
                                               List<AccountRef> contas) {
        if (envelope.getMessageSet(MessageSetType.banking) instanceof BankingResponseMessageSet banco) {
            for (BankStatementResponseTransaction t : banco.getStatementResponses()) {
                BankAccountDetails conta = t.getMessage().getAccount();
                extratos.add(t.getMessage());
                contas.add(new AccountRef(
                        conta == null ? null : conta.getBankId(),
                        conta == null ? null : conta.getAccountNumber(),
                        false));
            }
        }
    }

    private static void coletarExtratosDeCartao(ResponseEnvelope envelope, List<StatementResponse> extratos,
                                                List<AccountRef> contas) {
        if (envelope.getMessageSet(MessageSetType.creditcard) instanceof CreditCardResponseMessageSet cartao) {
            for (CreditCardStatementResponseTransaction t : cartao.getStatementResponses()) {
                var conta = t.getMessage().getAccount();
                extratos.add(t.getMessage());
                contas.add(new AccountRef(null, conta == null ? null : conta.getAccountNumber(), true));
            }
        }
    }

    private static ParsedStatement montar(StatementResponse extrato, AccountRef conta) {
        TransactionList lista = extrato.getTransactionList();
        List<Transaction> origem = (lista == null || lista.getTransactions() == null) ? List.of() : lista.getTransactions();

        List<RawTransaction> transacoes = new ArrayList<>(origem.size());
        for (int i = 0; i < origem.size(); i++) {
            transacoes.add(converter(origem.get(i), i + 1));
        }

        BalanceInfo saldo = extrato.getLedgerBalance();
        return new ParsedStatement(
                conta,
                lista == null ? null : OfxDataLiteralConversion.paraLocalDate(lista.getStart()),
                lista == null ? null : OfxDataLiteralConversion.paraLocalDate(lista.getEnd()),
                saldo == null ? null : saldo.getBigDecimalAmount(),
                saldo == null ? null : OfxDataLiteralConversion.paraLocalDate(saldo.getAsOfDate()),
                transacoes);
    }

    /** @param posicao posição no arquivo (1, 2, ...), só para a mensagem de erro */
    private static RawTransaction converter(Transaction t, int posicao) {
        LocalDate data = OfxDataLiteralConversion.paraLocalDate(t.getDatePosted());
        // getBigDecimalAmount lê o texto do TRNAMT sem passar por double (estado §7, item 6).
        BigDecimal valor = t.getBigDecimalAmount();
        if (data == null || valor == null) {
            throw new ArquivoInvalidoException("Transação nº " + posicao + " do arquivo está sem data ou sem valor.");
        }
        String descricao = t.getMemo() != null ? t.getMemo() : (t.getName() != null ? t.getName() : "");
        return new RawTransaction(t.getId(), data, valor, descricao.strip());
    }
}
