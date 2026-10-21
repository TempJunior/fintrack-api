package io.github.tempjr.fintrack.parser;

/**
 * Conta declarada dentro do arquivo (BANKACCTFROM ou CCACCTFROM).
 * Dado sensível: nunca logar (RNF08).
 *
 * @param bankId           BANKID; nulo em extrato de cartão
 * @param acctId           ACCTID (número da conta ou do cartão)
 * @param cartaoDeCredito  true se o extrato é de cartão (CREDITCARDMSGSRSV1)
 */
public record AccountRef(String bankId, String acctId, boolean cartaoDeCredito) {

    @Override
    public String toString() {
        return "AccountRef{cartaoDeCredito=" + cartaoDeCredito + "}";
    }
}
