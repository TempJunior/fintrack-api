package io.github.tempjr.fintrack.model.enums;

/**
 * Bancos suportados no MVP (D-12). O código é o número do banco no Banco Central,
 * o mesmo que aparece no BANKID do OFX.
 */
public enum Instituicao {
    ITAU("0341"),
    NUBANK("0260"),
    MERCADOPAGO("0323");

    private final String codigo;

    Instituicao(String codigo) {
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
