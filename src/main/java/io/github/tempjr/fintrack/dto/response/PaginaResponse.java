package io.github.tempjr.fintrack.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Página de resultados (RNF03). Formato próprio e estável para o app (Paging 3), em vez de serializar o
 * Page do Spring Data, cujo JSON não é um contrato estável.
 *
 * @param pagina começa em 0
 */
public record PaginaResponse<T>(List<T> conteudo, int pagina, int tamanho, long totalElementos, int totalPaginas,
                                boolean ultima) {

    public static <E, T> PaginaResponse<T> from(Page<E> page, Function<E, T> conversor) {
        return new PaginaResponse<>(
                page.getContent().stream().map(conversor).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
