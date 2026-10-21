package io.github.tempjr.fintrack.service;

import io.github.tempjr.fintrack.model.Conta;
import io.github.tempjr.fintrack.model.Transacao;
import io.github.tempjr.fintrack.model.enums.Instituicao;
import io.github.tempjr.fintrack.model.enums.TipoConta;
import io.github.tempjr.fintrack.repository.ContaRepository;
import io.github.tempjr.fintrack.repository.TransacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Idempotência da gravação contra o banco de verdade (RF07, RNF04). */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TransacaoServiceImpl.class)
@DisplayName("TransacaoService (integração)")
class TransacaoServiceIntegrationTest {

    @Autowired
    private TransacaoService transacaoService;

    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private TransacaoRepository transacaoRepository;

    private Conta conta;

    @BeforeEach
    void setUp() {
        conta = contaRepository.save(new Conta(Instituicao.ITAU, TipoConta.CORRENTE, "Itaú"));
    }

    /** Simula ler o arquivo de novo: objetos novos, mesmos dados. */
    private List<Transacao> lerExtrato(String... fitIds) {
        return java.util.Arrays.stream(fitIds)
                .map(fit -> new Transacao(conta, fit, LocalDate.of(2026, 6, 10), new BigDecimal("-10.00"), "PIX"))
                .toList();
    }

    @Test
    @DisplayName("importar o mesmo extrato duas vezes não duplica nada")
    void reimportacaoIdempotente() {
        ResultadoDedup primeira = transacaoService.salvarNovas(conta, lerExtrato("A", "B", "C"));
        ResultadoDedup segunda = transacaoService.salvarNovas(conta, lerExtrato("A", "B", "C"));

        assertThat(primeira.novas()).hasSize(3);
        assertThat(segunda.novas()).isEmpty();
        assertThat(segunda.duplicadas()).isEqualTo(3);
        assertThat(transacaoRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("período sobreposto grava só as transações novas")
    void periodoSobreposto() {
        transacaoService.salvarNovas(conta, lerExtrato("A", "B", "C"));

        ResultadoDedup sobreposto = transacaoService.salvarNovas(conta, lerExtrato("B", "C", "D", "E"));

        assertThat(sobreposto.novas()).extracting(Transacao::getDedupKey).containsExactly("D", "E");
        assertThat(sobreposto.duplicadas()).isEqualTo(2);
        assertThat(transacaoRepository.count()).isEqualTo(5);
    }
}
