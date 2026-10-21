package io.github.tempjr.fintrack.service;

import io.github.tempjr.fintrack.exception.ConflitoException;
import io.github.tempjr.fintrack.exception.RecursoNaoEncontradoException;
import io.github.tempjr.fintrack.model.CategorizadorPorRegras;
import io.github.tempjr.fintrack.model.Categoria;
import io.github.tempjr.fintrack.model.RegraCategorizacao;
import io.github.tempjr.fintrack.model.Transacao;
import io.github.tempjr.fintrack.model.enums.Operador;
import io.github.tempjr.fintrack.repository.CategoriaRepository;
import io.github.tempjr.fintrack.repository.RegraCategorizacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CategorizacaoServiceImpl implements CategorizacaoService {

    private static final Logger log = LoggerFactory.getLogger(CategorizacaoServiceImpl.class);

    private final CategoriaRepository categoriaRepository;
    private final RegraCategorizacaoRepository regraRepository;

    public CategorizacaoServiceImpl(CategoriaRepository categoriaRepository,
                                    RegraCategorizacaoRepository regraRepository) {
        this.categoriaRepository = categoriaRepository;
        this.regraRepository = regraRepository;
    }

    @Override
    @Transactional
    public Categoria criarCategoria(String nome, Long categoriaPaiId) {
        Categoria pai = categoriaPaiId == null ? null : buscarCategoria(categoriaPaiId);
        Categoria categoria = new Categoria(nome, pai); // valida nome e limite de 2 níveis
        garantirNomeLivre(categoria.getNome(), categoriaPaiId, null);
        return categoriaRepository.save(categoria);
    }

    @Override
    public List<Categoria> listarCategorias() {
        return categoriaRepository.findAllByOrderByNomeAsc();
    }

    @Override
    public Categoria buscarCategoria(Long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria", id));
    }

    @Override
    @Transactional
    public Categoria renomearCategoria(Long id, String novoNome) {
        Categoria categoria = buscarCategoria(id);
        categoria.renomear(novoNome);
        Long paiId = categoria.getPai() == null ? null : categoria.getPai().getId();
        garantirNomeLivre(categoria.getNome(), paiId, id);
        return categoria;
    }

    /**
     * A constraint do banco também barra nome repetido, mas diferencia maiúsculas e devolveria um erro
     * genérico. Aqui a regra é mais rígida e a mensagem é clara.
     */
    private void garantirNomeLivre(String nome, Long paiId, Long ignorarId) {
        if (categoriaRepository.existeNomeNoNivel(nome, paiId, ignorarId)) {
            throw new ConflitoException("Já existe a categoria \"" + nome + "\" neste nível.");
        }
    }

    @Override
    @Transactional
    public RegraCategorizacao criarRegra(Long categoriaId, Operador operador, String padrao, int prioridade) {
        RegraCategorizacao regra = new RegraCategorizacao(buscarCategoria(categoriaId), operador, padrao, prioridade);
        RegraCategorizacao salva = regraRepository.save(regra);
        log.info("Regra {} criada ({}) para a categoria {}", salva.getId(), operador, categoriaId);
        return salva;
    }

    @Override
    public List<RegraCategorizacao> listarRegras() {
        return regraRepository.findAllComCategoria();
    }

    @Override
    @Transactional
    public void excluirRegra(Long id) {
        RegraCategorizacao regra = regraRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Regra", id));
        regraRepository.delete(regra);
        log.info("Regra {} excluída", id);
    }

    @Override
    public CategorizadorPorRegras categorizador() {
        return new CategorizadorPorRegras(regraRepository.findAllComCategoria());
    }

    @Override
    public int aplicarRegras(List<Transacao> transacoes) {
        CategorizadorPorRegras categorizador = categorizador();
        if (categorizador.semRegras()) {
            return 0;
        }
        int categorizadas = 0;
        for (Transacao transacao : transacoes) {
            var categoria = categorizador.categorizar(transacao.getDescricaoOriginal());
            if (categoria.isPresent() && transacao.categorizarPorRegra(categoria.get())) {
                categorizadas++;
            }
        }
        return categorizadas;
    }
}
