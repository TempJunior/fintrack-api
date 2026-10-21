package io.github.tempjr.fintrack.model;

import io.github.tempjr.fintrack.model.enums.Instituicao;
import io.github.tempjr.fintrack.model.enums.TipoConta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Conta")
class ContaTest {

    @Test
    @DisplayName("nasce ativa, em BRL e sem número externo")
    void valoresPadrao() {
        Conta conta = new Conta(Instituicao.NUBANK, TipoConta.CARTAO, " Nubank cartão ");

        assertThat(conta.isAtiva()).isTrue();
        assertThat(conta.getMoeda()).isEqualTo("BRL");
        assertThat(conta.getNumeroExterno()).isNull();
        assertThat(conta.getApelido()).isEqualTo("Nubank cartão");
    }

    @Test
    @DisplayName("inativar não apaga, só marca como inativa (RF02)")
    void inativar() {
        Conta conta = new Conta(Instituicao.ITAU, TipoConta.CORRENTE, "Itaú");

        conta.inativar();

        assertThat(conta.isAtiva()).isFalse();
    }

    @Test
    @DisplayName("rejeita apelido vazio")
    void apelidoVazio() {
        Conta conta = new Conta(Instituicao.ITAU, TipoConta.CORRENTE, "Itaú");

        assertThatThrownBy(() -> conta.renomear("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("código da instituição é o BANKID do OFX (D-12)")
    void codigoInstituicao() {
        assertThat(Instituicao.ITAU.getCodigo()).isEqualTo("0341");
    }
}
