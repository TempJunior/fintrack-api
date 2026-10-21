package io.github.tempjr.fintrack.service;

import io.github.tempjr.fintrack.model.CategorizadorPorRegras;
import io.github.tempjr.fintrack.model.Categoria;
import io.github.tempjr.fintrack.model.RegraCategorizacao;
import io.github.tempjr.fintrack.model.Transacao;
import io.github.tempjr.fintrack.model.enums.Operador;

import java.util.List;

/** Categorias, regras e sua aplicação (RF15–RF20). Dono de CategoriaRepository e RegraCategorizacaoRepository. */
public interface CategorizacaoService {

    /** @param categoriaPaiId nulo = categoria raiz */
    Categoria criarCategoria(String nome, Long categoriaPaiId);

    /** Todas as categorias, raiz e subcategorias, ordenadas pelo nome. */
    List<Categoria> listarCategorias();

    Categoria buscarCategoria(Long id);

    Categoria renomearCategoria(Long id, String novoNome);

    RegraCategorizacao criarRegra(Long categoriaId, Operador operador, String padrao, int prioridade);

    /** Em ordem de avaliação, com a categoria carregada. */
    List<RegraCategorizacao> listarRegras();

    void excluirRegra(Long id);

    /** Categorizador com as regras atuais. Carrega as regras uma vez; use para um lote inteiro. */
    CategorizadorPorRegras categorizador();

    /**
     * Aplica as regras nas transações (RF17). Não sobrescreve escolha manual (RF18).
     *
     * @return quantas foram categorizadas
     */
    int aplicarRegras(List<Transacao> transacoes);
}
