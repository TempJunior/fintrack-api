package io.github.tempjr.fintrack.service;

import io.github.tempjr.fintrack.exception.RecursoNaoEncontradoException;
import io.github.tempjr.fintrack.model.Conta;
import io.github.tempjr.fintrack.model.enums.Instituicao;
import io.github.tempjr.fintrack.model.enums.TipoConta;
import io.github.tempjr.fintrack.repository.ContaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ContaServiceImpl implements ContaService {

    private static final Logger log = LoggerFactory.getLogger(ContaServiceImpl.class);

    private final ContaRepository contaRepository;

    public ContaServiceImpl(ContaRepository contaRepository) {
        this.contaRepository = contaRepository;
    }

    @Override
    @Transactional
    public Conta criar(Instituicao instituicao, TipoConta tipo, String apelido) {
        // Várias contas na mesma instituição são válidas (ex.: corrente + cartão Itaú).
        Conta conta = contaRepository.save(new Conta(instituicao, tipo, apelido));
        log.info("Conta {} criada ({}, {})", conta.getId(), instituicao, tipo);
        return conta;
    }

    @Override
    public List<Conta> listar(Boolean ativa) {
        return ativa == null
                ? contaRepository.findAllByOrderByApelidoAsc()
                : contaRepository.findByAtivaOrderByApelidoAsc(ativa);
    }

    @Override
    public Conta buscarPorId(Long id) {
        return contaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta", id));
    }

    @Override
    @Transactional
    public Conta renomear(Long id, String novoApelido) {
        Conta conta = buscarPorId(id);
        conta.renomear(novoApelido);
        // Sem save(): a entidade está gerenciada dentro da transação, e o Hibernate grava a mudança
        // no commit (dirty checking).
        return conta;
    }

    @Override
    @Transactional
    public void inativar(Long id) {
        buscarPorId(id).inativar();
        log.info("Conta {} inativada", id);
    }
}
