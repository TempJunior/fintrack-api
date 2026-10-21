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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContaServiceImpl")
class ContaServiceImplTest {

    @Mock
    private ContaRepository contaRepository;

    @InjectMocks
    private ContaServiceImpl contaService;

    @Test
    @DisplayName("permite duas contas na mesma instituição (corrente e cartão Itaú)")
    void duasContasMesmaInstituicao() {
        Conta corrente = new Conta(Instituicao.ITAU, TipoConta.CORRENTE, "Itaú corrente");
        Conta cartao = new Conta(Instituicao.ITAU, TipoConta.CARTAO, "Itaú cartão");
        when(contaRepository.save(corrente)).thenReturn(corrente);
        when(contaRepository.save(cartao)).thenReturn(cartao);

        contaService.criarConta(corrente);
        contaService.criarConta(cartao);

        verify(contaRepository).save(corrente);
        verify(contaRepository).save(cartao);
    }

    @Test
    @DisplayName("buscarPorId devolve a conta quando existe")
    void buscarExistente() {
        Conta conta = new Conta(Instituicao.ITAU, TipoConta.CORRENTE, "Itaú");
        when(contaRepository.findById(1L)).thenReturn(Optional.of(conta));

        assertThat(contaService.buscarPorId(1L)).isSameAs(conta);
    }

    @Test
    @DisplayName("buscarPorId lança RecursoNaoEncontradoException quando não existe")
    void buscarInexistente() {
        when(contaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contaService.buscarPorId(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("99");
    }
}
