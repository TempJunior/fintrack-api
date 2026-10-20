package io.github.tempjr.fintrack.domain.services;

import io.github.tempjr.fintrack.domain.enuns.Instituicao;
import io.github.tempjr.fintrack.domain.enuns.TipoConta;
import io.github.tempjr.fintrack.domain.model.Conta;
import io.github.tempjr.fintrack.domain.services.interfaces.ContaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ImportServiceImplTest {

    @Autowired
    private ImportServiceImpl importService;
    private final File file = new File("arquivos/itau.ofx");
    @Autowired
    private ContaServiceImpl contaService;

    @Test
    void importar() {
        //contaService.criarConta(new Conta(0341L, "Itau", Instituicao.ITAU, TipoConta.CORRENTE, true, null));
        int linhas = importService.importar(0341L, file);
        var conta = importService.buscarConta(0341L);
        System.out.println("Linhas importadas: " + linhas);

        System.out.println("Dados importados do banco: ");
        importService.listarTransacoes(conta).forEach(System.out::println);
    }

    @Test
    void listar() {
        var conta = importService.buscarConta(0341L);
        importService.listarTransacoes(conta).forEach(System.out::println);
    }
}