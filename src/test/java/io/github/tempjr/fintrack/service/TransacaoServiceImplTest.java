package io.github.tempjr.fintrack.service;

import io.github.tempjr.fintrack.model.Conta;
import io.github.tempjr.fintrack.model.Transacao;
import io.github.tempjr.fintrack.model.enums.Instituicao;
import io.github.tempjr.fintrack.model.enums.TipoConta;
import io.github.tempjr.fintrack.repository.TransacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransacaoServiceImpl")
class TransacaoServiceImplTest {

    @Mock
    private TransacaoRepository transacaoRepository;

    @Mock
    private CategorizacaoService categorizacaoService;

    @InjectMocks
    private TransacaoServiceImpl transacaoService;

    private Conta conta;

    @BeforeEach
    void setUp() {
        conta = contaComId(1L);
    }

    private static Conta contaComId(Long id) {
        Conta c = new Conta(Instituicao.ITAU, TipoConta.CORRENTE, "Itaú");
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private Transacao transacao(Conta c, String fitId) {
        return new Transacao(c, fitId, LocalDate.of(2026, 6, 10), new BigDecimal("-10.00"), "PIX");
    }

    /** saveAll do mock devolve o que recebeu, como o real faria. */
    private void saveAllDevolveOQueRecebe() {
        when(transacaoRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("grava todas quando nenhuma existe")
    void todasNovas() {
        when(transacaoRepository.findDedupKeysExistentes(eq(1L), anyCollection())).thenReturn(Set.of());
        saveAllDevolveOQueRecebe();

        ResultadoDedup resultado = transacaoService.salvarNovas(conta,
                List.of(transacao(conta, "A"), transacao(conta, "B")));

        assertThat(resultado.novas()).extracting(Transacao::getDedupKey).containsExactly("A", "B");
        assertThat(resultado.duplicadas()).isZero();
        assertThat(resultado.totalLido()).isEqualTo(2);
    }

    @Test
    @DisplayName("ignora as que já existem na conta (reimportação)")
    void ignoraExistentes() {
        when(transacaoRepository.findDedupKeysExistentes(eq(1L), anyCollection())).thenReturn(Set.of("A"));
        saveAllDevolveOQueRecebe();

        ResultadoDedup resultado = transacaoService.salvarNovas(conta,
                List.of(transacao(conta, "A"), transacao(conta, "B")));

        assertThat(resultado.novas()).extracting(Transacao::getDedupKey).containsExactly("B");
        assertThat(resultado.duplicadas()).isEqualTo(1);
    }

    @Test
    @DisplayName("ignora repetição dentro do próprio arquivo")
    void ignoraRepetidaNoLote() {
        when(transacaoRepository.findDedupKeysExistentes(eq(1L), anyCollection())).thenReturn(Set.of());
        saveAllDevolveOQueRecebe();

        ResultadoDedup resultado = transacaoService.salvarNovas(conta,
                List.of(transacao(conta, "A"), transacao(conta, "A")));

        assertThat(resultado.novas()).hasSize(1);
        assertThat(resultado.duplicadas()).isEqualTo(1);
    }

    @Test
    @DisplayName("consulta o banco uma vez só, com todas as chaves")
    void umaConsultaSo() {
        when(transacaoRepository.findDedupKeysExistentes(eq(1L), anyCollection())).thenReturn(Set.of());
        saveAllDevolveOQueRecebe();

        transacaoService.salvarNovas(conta,
                List.of(transacao(conta, "A"), transacao(conta, "B"), transacao(conta, "C")));

        verify(transacaoRepository).findDedupKeysExistentes(1L, Set.of("A", "B", "C"));
    }

    @Test
    @DisplayName("lista vazia não toca no banco")
    void listaVazia() {
        ResultadoDedup resultado = transacaoService.salvarNovas(conta, List.of());

        assertThat(resultado.totalLido()).isZero();
        verifyNoInteractions(transacaoRepository);
    }

    @Test
    @DisplayName("rejeita transação de outra conta (RF03)")
    void outraConta() {
        Conta outra = contaComId(2L);

        assertThatThrownBy(() -> transacaoService.salvarNovas(conta,
                List.of(transacao(conta, "A"), transacao(outra, "B"))))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(transacaoRepository);
    }

    @Test
    @DisplayName("aceita a mesma conta em outra instância (compara por id)")
    void mesmaContaOutraInstancia() {
        when(transacaoRepository.findDedupKeysExistentes(eq(1L), anyCollection())).thenReturn(Set.of());
        saveAllDevolveOQueRecebe();
        Conta mesmaConta = contaComId(1L);

        ResultadoDedup resultado = transacaoService.salvarNovas(conta, List.of(transacao(mesmaConta, "A")));

        assertThat(resultado.novas()).hasSize(1);
    }
}
