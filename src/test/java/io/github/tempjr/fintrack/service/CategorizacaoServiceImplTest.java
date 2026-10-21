package io.github.tempjr.fintrack.service;

import io.github.tempjr.fintrack.exception.ConflitoException;
import io.github.tempjr.fintrack.exception.RecursoNaoEncontradoException;
import io.github.tempjr.fintrack.model.Categoria;
import io.github.tempjr.fintrack.model.Conta;
import io.github.tempjr.fintrack.model.RegraCategorizacao;
import io.github.tempjr.fintrack.model.Transacao;
import io.github.tempjr.fintrack.model.enums.Instituicao;
import io.github.tempjr.fintrack.model.enums.Operador;
import io.github.tempjr.fintrack.model.enums.OrigemCategoria;
import io.github.tempjr.fintrack.model.enums.TipoConta;
import io.github.tempjr.fintrack.repository.CategoriaRepository;
import io.github.tempjr.fintrack.repository.RegraCategorizacaoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategorizacaoServiceImpl")
class CategorizacaoServiceImplTest {

    @Mock
    private CategoriaRepository categoriaRepository;
    @Mock
    private RegraCategorizacaoRepository regraRepository;
    @InjectMocks
    private CategorizacaoServiceImpl service;

    private final Conta conta = new Conta(Instituicao.ITAU, TipoConta.CORRENTE, "Itaú");

    private Transacao transacao(String descricao) {
        return new Transacao(conta, null, LocalDate.of(2026, 6, 1), new BigDecimal("-10.00"), descricao);
    }

    @Test
    @DisplayName("nome repetido no mesmo nível: ConflitoException, sem gravar")
    void nomeRepetido() {
        when(categoriaRepository.existeNomeNoNivel("Alimentação", null, null)).thenReturn(true);

        assertThatThrownBy(() -> service.criarCategoria("Alimentação", null))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("Alimentação");
        verify(categoriaRepository, never()).save(any());
    }

    @Test
    @DisplayName("subcategoria com pai inexistente: 404")
    void paiInexistente() {
        when(categoriaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criarCategoria("Restaurante", 99L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("aplicarRegras categoriza as que casam e não mexe nas manuais (RF17, RF18)")
    void aplicarRegras() {
        Categoria alimentacao = new Categoria("Alimentação");
        Categoria trabalho = new Categoria("Trabalho");
        when(regraRepository.findAllComCategoria()).thenReturn(
                List.of(new RegraCategorizacao(alimentacao, Operador.CONTAINS, "padaria", 10)));
        Transacao padaria = transacao("PADARIA SÃO JOÃO");
        Transacao padariaManual = transacao("PADARIA DO TRABALHO");
        padariaManual.categorizarManualmente(trabalho);
        Transacao outra = transacao("UBER");

        int categorizadas = service.aplicarRegras(List.of(padaria, padariaManual, outra));

        assertThat(categorizadas).isEqualTo(1);
        assertThat(padaria.getCategoria()).isSameAs(alimentacao);
        assertThat(padaria.getOrigemCategoria()).isEqualTo(OrigemCategoria.REGRA);
        assertThat(padariaManual.getCategoria()).isSameAs(trabalho);
        assertThat(outra.getCategoria()).isNull();
    }

    @Test
    @DisplayName("sem regras, aplicarRegras não faz nada")
    void semRegras() {
        when(regraRepository.findAllComCategoria()).thenReturn(List.of());

        assertThat(service.aplicarRegras(List.of(transacao("PADARIA")))).isZero();
    }
}
