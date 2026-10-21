package io.github.tempjr.fintrack.domain.services;

import io.github.tempjr.fintrack.domain.model.Conta;
import io.github.tempjr.fintrack.domain.services.interfaces.ContaService;
import io.github.tempjr.fintrack.repository.ContaRepository;
import org.springframework.stereotype.Service;

@Service
public class ContaServiceImpl implements ContaService {

    private final ContaRepository contaRepository;

    public ContaServiceImpl(ContaRepository contaRepository) {
        this.contaRepository = contaRepository;
    }

    @Override
    public void criarConta(Conta conta) {
        try{
            validacaoDeConta(conta);
            contaRepository.save(conta);
        } catch (RuntimeException e){
            throw new RuntimeException("Erro ao criar conta: " + e.getMessage());
        }
    }

    void validacaoDeConta(Conta conta){
        if (contaRepository.existsByApelidoOrInstituicao(conta.getApelido(), conta.getInstituicao())){
            throw new RuntimeException("Conta já existe com mesmo apelido ou instituição.");
        }
    }
}
