package io.github.tempjr.fintrack.service;

import io.github.tempjr.fintrack.model.Conta;

public interface ContaService {

    Conta criarConta(Conta conta);

    /** @throws io.github.tempjr.fintrack.exception.RecursoNaoEncontradoException se não existir */
    Conta buscarPorId(Long id);
}
