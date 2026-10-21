package io.github.tempjr.fintrack.dto.response;

import io.github.tempjr.fintrack.model.Conta;
import io.github.tempjr.fintrack.model.enums.Instituicao;
import io.github.tempjr.fintrack.model.enums.TipoConta;

/** Conta como sai na API. Sem numeroExterno (dado sensível). */
public record ContaResponse(
        Long id,
        Instituicao instituicao,
        String codigoInstituicao,
        TipoConta tipo,
        String apelido,
        String moeda,
        boolean ativa
) {

    public static ContaResponse from(Conta conta) {
        return new ContaResponse(
                conta.getId(),
                conta.getInstituicao(),
                conta.getInstituicao().getCodigo(),
                conta.getTipo(),
                conta.getApelido(),
                conta.getMoeda(),
                conta.isAtiva());
    }
}
