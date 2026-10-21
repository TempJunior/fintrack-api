package io.github.tempjr.fintrack.service;

import io.github.tempjr.fintrack.model.Conta;
import io.github.tempjr.fintrack.model.enums.Instituicao;
import io.github.tempjr.fintrack.model.enums.TipoConta;

import java.util.List;

/** Contas financeiras (RF01–RF03). Recebe e devolve entidades; DTOs ficam no controller. */
public interface ContaService {

    Conta criar(Instituicao instituicao, TipoConta tipo, String apelido);

    /** @param ativa filtro opcional: null = todas */
    List<Conta> listar(Boolean ativa);

    /** @throws io.github.tempjr.fintrack.exception.RecursoNaoEncontradoException se não existir */
    Conta buscarPorId(Long id);

    Conta renomear(Long id, String novoApelido);

    /** Contas não são excluídas, só inativadas (RF02). Inativar de novo não é erro. */
    void inativar(Long id);
}
