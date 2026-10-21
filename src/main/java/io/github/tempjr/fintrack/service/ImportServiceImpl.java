package io.github.tempjr.fintrack.domain.services;

import com.webcohesion.ofx4j.domain.data.MessageSetType;
import com.webcohesion.ofx4j.domain.data.ResponseEnvelope;
import com.webcohesion.ofx4j.domain.data.ResponseMessage;
import com.webcohesion.ofx4j.domain.data.ResponseMessageSet;
import com.webcohesion.ofx4j.domain.data.banking.BankStatementResponseTransaction;
import com.webcohesion.ofx4j.domain.data.banking.BankingResponseMessageSet;
import com.webcohesion.ofx4j.domain.data.common.Transaction;
import com.webcohesion.ofx4j.io.AggregateUnmarshaller;
import io.github.tempjr.fintrack.domain.model.Conta;
import io.github.tempjr.fintrack.domain.model.Transacao;
import io.github.tempjr.fintrack.domain.services.interfaces.ImportService;
import io.github.tempjr.fintrack.repository.ContaRepository;
import io.github.tempjr.fintrack.repository.TransacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ImportServiceImpl implements ImportService {

    private final ContaRepository contaRepository;
    private final TransacaoRepository transacaoRepository;

    public ImportServiceImpl(TransacaoRepository transacaoRepository, ContaRepository contaRepository) {
        this.transacaoRepository = transacaoRepository;
        this.contaRepository = contaRepository;
    }

    @Override
    @Transactional
    public int importar(long contaId, File file) {
        Conta conta = buscarConta(contaId);

        Set<Transacao> listaTransacoes = new HashSet<Transacao>();

        if (file == null || conta == null || !conta.isAtiva()) {
            throw new IllegalArgumentException("Arquivo não pode ser nulo");
        }

        try {
            AggregateUnmarshaller au = new AggregateUnmarshaller(ResponseEnvelope.class);
            ResponseEnvelope envelope = (ResponseEnvelope) au.unmarshal(new FileInputStream(file));

            MessageSetType type = MessageSetType.banking;
            ResponseMessageSet messageSet = envelope.getMessageSet(type);

            if (messageSet != null) {
                Transacao extrato;

                List<ResponseMessage> bank = ((BankingResponseMessageSet) messageSet).getResponseMessages();
                for (int i = 0; i < bank.size(); i++) {
                    BankStatementResponseTransaction bs = (BankStatementResponseTransaction) bank.get(i);
                    List<Transaction> transacoes = bs.getMessage().getTransactionList().getTransactions();

                    for (int j = 0; j < transacoes.size(); j++) {
                        Transaction transacao = transacoes.get(j);
                        extrato = new Transacao();

                        extrato.setId(Long.valueOf(transacao.getId()));
                        extrato.setData(transacao.getDatePosted().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
                        extrato.setDescricao(transacao.getMemo());
                        extrato.setValor(BigDecimal.valueOf(transacao.getAmount()));
                        extrato.setConta(conta);

                        listaTransacoes.add(extrato);
                        conta.setTransacoes(listaTransacoes);

                        System.out.println("Transação importada: " + extrato.getData() + " - " + extrato.getDescricao() + " - " + extrato.getValor());
                    }
                    salvarTransacoes(listaTransacoes.stream().toList());
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Erro ao importar arquivo OFX: " + e.getMessage(), e);
        }
        return listaTransacoes.size();
    }

    @Override
    public Conta buscarConta(long contaId) {
        return contaRepository.findById(contaId).orElseThrow(() -> new RuntimeException("Conta não encontrada: " + contaId));
    }

    @Override
    public List<Transacao> listarTransacoes(Conta conta) {
        return transacaoRepository.findAll();
    }

    @Override
    @Transactional
    public void salvarTransacoes(List<Transacao> transacoes) {
        if (transacoes == null || transacoes.isEmpty()) {
            throw new IllegalArgumentException("Lista de transações não pode ser nula ou vazia");
        }

        for (Transacao transacao : transacoes) {
            if (transacaoRepository.existsById(transacao.getId())) {
                System.out.println("Transação já existe: " + transacao.getId());
            } else {
                transacaoRepository.save(transacao);
                System.out.println("Transação salva: " + transacao.getId());
            }
        }
    }
}
