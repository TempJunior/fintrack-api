package io.github.tempjr.fintrack.domain.services.interfaces;

import io.github.tempjr.fintrack.domain.model.Conta;
import io.github.tempjr.fintrack.domain.model.Transacao;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.io.File;
import java.util.List;

public interface ImportService {
    int importar(long contaId, File file);
    Conta buscarConta(long contaId);

    @Query("SELECT t FROM Transacao t WHERE t.conta = :contaId")
    List<Transacao> listarTransacoes(@Param("conta") Conta conta);
    void salvarTransacoes(List<Transacao> transacoes);
}
