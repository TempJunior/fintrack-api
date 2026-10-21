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
    @DisplayName("primeira importação vincula o número; as seguintes conferem (D-09)")
    void numeroExterno() {
        Conta conta = new Conta(Instituicao.ITAU, TipoConta.CORRENTE, "Itaú");

        assertThat(conta.conferirOuVincularNumeroExterno(" 12345 ")).isTrue();
        assertThat(conta.getNumeroExterno()).isEqualTo("12345");
        assertThat(conta.conferirOuVincularNumeroExterno("12345")).isTrue();
        assertThat(conta.conferirOuVincularNumeroExterno("99999")).isFalse();
        assertThat(conta.getNumeroExterno()).isEqualTo("12345");
    }

    @Test
    @DisplayName("arquivo sem número de conta não vincula nem rejeita")
    void numeroExternoAusente() {
        Conta conta = new Conta(Instituicao.ITAU, TipoConta.CORRENTE, "Itaú");

        assertThat(conta.conferirOuVincularNumeroExterno(null)).isTrue();
        assertThat(conta.getNumeroExterno()).isNull();
    }

    @Test
    @DisplayName("código da instituição é o BANKID do OFX (D-12)")
    void codigoInstituicao() {
        assertThat(Instituicao.ITAU.getCodigo()).isEqualTo("0341");
    }
}
