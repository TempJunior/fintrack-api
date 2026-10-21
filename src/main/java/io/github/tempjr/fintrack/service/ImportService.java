package io.github.tempjr.fintrack.service;

import java.io.File;

public interface ImportService {

    /**
     * Protótipo: lê um OFX do disco e grava as transações novas.
     * Será substituído na Etapa 4 (upload multipart, parser via Strategy, validação, auditoria).
     *
     * @return quantidade de transações novas gravadas
     */
    int importar(Long contaId, File arquivo);
}
