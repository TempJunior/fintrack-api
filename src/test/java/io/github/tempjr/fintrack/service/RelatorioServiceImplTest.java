package io.github.tempjr.fintrack.service;

import io.github.tempjr.fintrack.model.Categoria;
import io.github.tempjr.fintrack.repository.RelatorioRepository.TotalCategoria;
import io.github.tempjr.fintrack.service.relatorio.TotalPorCategoria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RelatorioServiceImpl (lógica em memória)")
class RelatorioServiceImplTest {

    private static Categoria comId(Categoria c, long id) {
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private static TotalCategoria linha(Long categoriaId, String receitas, String despesas, long qtd) {
        return new TotalCategoria(categoriaId, new BigDecimal(receitas), new BigDecimal(despesas), qtd);
    }

    @Test
    @DisplayName("soma subcategorias na raiz, ordena por despesa e deixa Não categorizado por último")
    void agruparPorRaiz() {
        Categoria alimentacao = comId(new Categoria("Alimentação"), 1);
        Categoria padaria = comId(new Categoria("Padaria", alimentacao), 2);
        Categoria restaurante = comId(new Categoria("Restaurante", alimentacao), 3);
        Categoria moradia = comId(new Categoria("Moradia"), 4);
        Map<Long, Categoria> categorias = Map.of(1L, alimentacao, 2L, padaria, 3L, restaurante, 4L, moradia);

        List<TotalPorCategoria> resultado = RelatorioServiceImpl.agruparPorRaiz(List.of(
                linha(null, "3500.00", "0.00", 1),
                linha(2L, "0.00", "42.10", 1),
                linha(3L, "0.00", "120.00", 2),
                linha(1L, "5.00", "10.00", 1),
                linha(4L, "0.00", "150.75", 1)), categorias);

        assertThat(resultado).extracting(TotalPorCategoria::nome)
                .containsExactly("Alimentação", "Moradia", "Não categorizado");
        TotalPorCategoria alim = resultado.get(0);
        assertThat(alim.resumo().despesas()).isEqualByComparingTo("172.10"); // 10 + 42.10 + 120
        assertThat(alim.resumo().receitas()).isEqualByComparingTo("5.00");
        assertThat(alim.quantidade()).isEqualTo(4);
        assertThat(alim.subcategorias()).extracting(TotalPorCategoria::nome).containsExactly("Restaurante", "Padaria");
    }

    @Test
    @DisplayName("raiz aparece mesmo sem lançamento direto, só com subcategorias")
    void raizSoComSubcategoria() {
        Categoria alimentacao = comId(new Categoria("Alimentação"), 1);
        Categoria padaria = comId(new Categoria("Padaria", alimentacao), 2);

        List<TotalPorCategoria> resultado = RelatorioServiceImpl.agruparPorRaiz(
                List.of(linha(2L, "0.00", "42.10", 1)), Map.of(1L, alimentacao, 2L, padaria));

        assertThat(resultado).singleElement().satisfies(r -> {
            assertThat(r.nome()).isEqualTo("Alimentação");
            assertThat(r.resumo().despesas()).isEqualByComparingTo("42.10");
        });
    }

    @Test
    @DisplayName("meses entre início e fim, inclusive; valida ordem e limite de 36")
    void mesesEntre() {
        assertThat(RelatorioServiceImpl.mesesEntre(YearMonth.of(2025, 11), YearMonth.of(2026, 2)))
                .containsExactly(YearMonth.of(2025, 11), YearMonth.of(2025, 12), YearMonth.of(2026, 1), YearMonth.of(2026, 2));
        assertThatThrownBy(() -> RelatorioServiceImpl.mesesEntre(YearMonth.of(2026, 2), YearMonth.of(2026, 1)))
                .hasMessageContaining("depois");
        assertThatThrownBy(() -> RelatorioServiceImpl.mesesEntre(YearMonth.of(2023, 1), YearMonth.of(2026, 1)))
                .hasMessageContaining("36");
    }
}
