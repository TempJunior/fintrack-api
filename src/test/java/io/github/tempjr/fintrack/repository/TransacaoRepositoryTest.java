package io.github.tempjr.fintrack.repository;

import io.github.tempjr.fintrack.model.Categoria;
import io.github.tempjr.fintrack.model.Conta;
import io.github.tempjr.fintrack.model.Transacao;
import io.github.tempjr.fintrack.model.enums.Instituicao;
import io.github.tempjr.fintrack.model.enums.OrigemCategoria;
import io.github.tempjr.fintrack.model.enums.TipoConta;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integração com o banco: schema do Flyway + mapeamento JPA + constraints.
 * Se uma entidade divergir da migration, o contexto nem sobe (ddl-auto: validate).
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // usa o H2 do application-test.yaml
@DisplayName("TransacaoRepository (integração)")
class TransacaoRepositoryTest {

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private TestEntityManager em;

    private Conta conta;

    @BeforeEach
    void setUp() {
        conta = contaRepository.save(new Conta(Instituicao.ITAU, TipoConta.CORRENTE, "Itaú corrente"));
    }

    private Transacao transacao(String fitId, String valor) {
        return new Transacao(conta, fitId, LocalDate.of(2026, 6, 10), new BigDecimal(valor), "PIX ENVIADO");
    }

    @Test
    @DisplayName("grava e lê de volta com valor em 2 casas e enriquecimento")
    void gravaELe() {
        Categoria transporte = em.persist(new Categoria("Transporte"));
        Transacao nova = transacao("FIT-1", "-35.9");
        nova.categorizarPorRegra(transporte);
        nova.adicionarTag("viagem");
        Long id = transacaoRepository.saveAndFlush(nova).getId();
        em.clear(); // força a leitura do banco, não do cache da sessão

        Transacao lida = transacaoRepository.findById(id).orElseThrow();

        assertThat(lida.getValor()).isEqualByComparingTo("-35.90");
        assertThat(lida.getDedupKey()).isEqualTo("FIT-1");
        assertThat(lida.getOrigemCategoria()).isEqualTo(OrigemCategoria.REGRA);
        assertThat(lida.getTags()).containsExactly("viagem");
    }

    @Test
    @DisplayName("existsByContaIdAndDedupKey encontra transação já gravada")
    void existsPorDedupKey() {
        transacaoRepository.saveAndFlush(transacao("FIT-1", "-10.00"));

        assertThat(transacaoRepository.existsByContaIdAndDedupKey(conta.getId(), "FIT-1")).isTrue();
        assertThat(transacaoRepository.existsByContaIdAndDedupKey(conta.getId(), "FIT-2")).isFalse();
    }

    @Test
    @DisplayName("banco rejeita a mesma dedup_key duas vezes na mesma conta (RF07)")
    void constraintDedup() {
        transacaoRepository.saveAndFlush(transacao("FIT-1", "-10.00"));

        assertThatThrownBy(() -> transacaoRepository.saveAndFlush(transacao("FIT-1", "-10.00")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("mesma dedup_key em contas diferentes é permitida")
    void mesmaChaveOutraConta() {
        Conta cartao = contaRepository.save(new Conta(Instituicao.ITAU, TipoConta.CARTAO, "Itaú cartão"));
        transacaoRepository.saveAndFlush(transacao("FIT-1", "-10.00"));

        Transacao naOutraConta = new Transacao(cartao, "FIT-1", LocalDate.of(2026, 6, 10),
                new BigDecimal("-10.00"), "PIX ENVIADO");

        assertThat(transacaoRepository.saveAndFlush(naOutraConta).getId()).isNotNull();
    }

    @Test
    @DisplayName("findByContaId devolve página só com as transações da conta (RNF03)")
    void paginaPorConta() {
        Conta outra = contaRepository.save(new Conta(Instituicao.NUBANK, TipoConta.CORRENTE, "Nubank"));
        transacaoRepository.save(transacao("FIT-1", "-10.00"));
        transacaoRepository.save(transacao("FIT-2", "-20.00"));
        transacaoRepository.save(transacao("FIT-3", "-30.00"));
        transacaoRepository.saveAndFlush(new Transacao(outra, "FIT-X", LocalDate.of(2026, 6, 10),
                new BigDecimal("5.00"), "OUTRA"));

        Page<Transacao> pagina = transacaoRepository.findByContaId(conta.getId(), PageRequest.of(0, 2));

        assertThat(pagina.getTotalElements()).isEqualTo(3);
        assertThat(pagina.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("banco rejeita duas categorias raiz com o mesmo nome")
    void categoriaRaizDuplicada() {
        em.persistAndFlush(new Categoria("Alimentação"));

        assertThatThrownBy(() -> em.persistAndFlush(new Categoria("Alimentação")))
                .isInstanceOf(PersistenceException.class); // independe do banco (H2 hoje, Postgres depois)
    }
}
