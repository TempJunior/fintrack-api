package io.github.tempjr.fintrack.service;

import io.github.tempjr.fintrack.exception.RecursoNaoEncontradoException;
import io.github.tempjr.fintrack.model.Conta;
import io.github.tempjr.fintrack.model.enums.Instituicao;
import io.github.tempjr.fintrack.model.enums.TipoConta;
import io.github.tempjr.fintrack.repository.ContaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContaServiceImpl")
class ContaServiceImplTest {

    @Mock
    private ContaRepository contaRepository;

    @InjectMocks
    private ContaServiceImpl contaService;

    @Test
    @DisplayName("criar monta a conta e salva")
    void criar() {
        when(contaRepository.save(any(Conta.class))).thenAnswer(inv -> inv.getArgument(0));

        Conta conta = contaService.criar(Instituicao.ITAU, TipoConta.CARTAO, "Itaú cartão");

        assertThat(conta.getInstituicao()).isEqualTo(Instituicao.ITAU);
        assertThat(conta.getTipo()).isEqualTo(TipoConta.CARTAO);
        assertThat(conta.isAtiva()).isTrue();
        verify(contaRepository).save(conta);
    }

    @Test
    @DisplayName("criar com apelido vazio falha antes de chegar no banco")
    void criarApelidoVazio() {
        assertThatThrownBy(() -> contaService.criar(Instituicao.ITAU, TipoConta.CORRENTE, " "))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(contaRepository);
    }

    @Test
    @DisplayName("listar sem filtro traz todas; com filtro, só ativas ou inativas")
    void listar() {
        List<Conta> todas = List.of(new Conta(Instituicao.ITAU, TipoConta.CORRENTE, "A"));
        List<Conta> ativas = List.of(new Conta(Instituicao.NUBANK, TipoConta.CORRENTE, "B"));
        when(contaRepository.findAllByOrderByApelidoAsc()).thenReturn(todas);
        when(contaRepository.findByAtivaOrderByApelidoAsc(true)).thenReturn(ativas);

        assertThat(contaService.listar(null)).isSameAs(todas);
        assertThat(contaService.listar(true)).isSameAs(ativas);
    }

    @Test
    @DisplayName("buscarPorId lança RecursoNaoEncontradoException quando não existe")
    void buscarInexistente() {
        when(contaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contaService.buscarPorId(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("renomear altera o apelido da conta existente")
    void renomear() {
        Conta conta = new Conta(Instituicao.ITAU, TipoConta.CORRENTE, "Antigo");
        when(contaRepository.findById(1L)).thenReturn(Optional.of(conta));

        Conta resultado = contaService.renomear(1L, "Novo");

        assertThat(resultado.getApelido()).isEqualTo("Novo");
    }

    @Test
    @DisplayName("inativar marca a conta como inativa")
    void inativar() {
        Conta conta = new Conta(Instituicao.ITAU, TipoConta.CORRENTE, "Itaú");
        when(contaRepository.findById(1L)).thenReturn(Optional.of(conta));

        contaService.inativar(1L);

        assertThat(conta.isAtiva()).isFalse();
    }

    @Test
    @DisplayName("inativar conta inexistente lança RecursoNaoEncontradoException")
    void inativarInexistente() {
        when(contaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contaService.inativar(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
