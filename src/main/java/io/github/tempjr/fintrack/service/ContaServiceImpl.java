package io.github.tempjr.fintrack.service;

import io.github.tempjr.fintrack.exception.RecursoNaoEncontradoException;
import io.github.tempjr.fintrack.model.Conta;
import io.github.tempjr.fintrack.repository.ContaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ContaServiceImpl implements ContaService {

    private final ContaRepository contaRepository;

    public ContaServiceImpl(ContaRepository contaRepository) {
        this.contaRepository = contaRepository;
    }

    @Override
    @Transactional
    public Conta criarConta(Conta conta) {
        // Várias contas na mesma instituição são válidas (ex.: corrente + cartão Itaú).
        return contaRepository.save(conta);
    }

    @Override
    public Conta buscarPorId(Long id) {
        return contaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta", id));
    }
}
