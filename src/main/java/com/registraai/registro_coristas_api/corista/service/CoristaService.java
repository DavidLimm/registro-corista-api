package com.registraai.registro_coristas_api.corista.service;

import com.registraai.registro_coristas_api.corista.dto.CoristaFiltro;
import com.registraai.registro_coristas_api.corista.dto.CoristaRequest;
import com.registraai.registro_coristas_api.corista.exception.CoristaNaoEncontradoException;
import com.registraai.registro_coristas_api.corista.exception.PromocaoInvalidaException;
import com.registraai.registro_coristas_api.corista.model.Corista;
import com.registraai.registro_coristas_api.corista.model.ListaClassificacao;
import com.registraai.registro_coristas_api.corista.repository.CoristaRepository;
import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.pessoa.service.PessoaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CoristaService {

    private final CoristaRepository coristaRepository;
    private final PessoaService pessoaService;
    private final Clock clock;

    /** Grava pessoa e corista na mesma transação. A lista de classificação nasce conforme a idade. */
    @Transactional
    public Corista criar(CoristaRequest request) {
        Pessoa pessoa = pessoaService.criar(request.pessoa());
        Corista corista = new Corista();
        corista.setPessoa(pessoa);
        preencher(corista, request);
        corista.setListaClassificacao(listaPelaIdade(pessoa));
        return coristaRepository.save(corista);
    }

    @Transactional(readOnly = true)
    public Corista buscarPorId(UUID id) {
        return coristaRepository.findById(id)
                .orElseThrow(() -> new CoristaNaoEncontradoException(id));
    }

    /** Ordenada por nome (o cliente não escolhe a ordenação). */
    @Transactional(readOnly = true)
    public Page<Corista> listar(CoristaFiltro filtro, int pagina, int tamanho) {
        Specification<Corista> especificacao = Specification.unrestricted();
        if (filtro.nome() != null && !filtro.nome().isBlank()) {
            String trecho = "%" + escaparLike(filtro.nome().trim().toLowerCase()) + "%";
            especificacao = especificacao.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("pessoa").get("nome")), trecho, '\\'));
        }
        if (filtro.areaId() != null) {
            especificacao = especificacao.and((root, query, cb) ->
                    cb.equal(root.get("pessoa").get("congregacao").get("area").get("id"), filtro.areaId()));
        }
        if (filtro.congregacaoId() != null) {
            especificacao = especificacao.and((root, query, cb) ->
                    cb.equal(root.get("pessoa").get("congregacao").get("id"), filtro.congregacaoId()));
        }
        if (filtro.listaClassificacao() != null) {
            especificacao = especificacao.and((root, query, cb) ->
                    cb.equal(root.get("listaClassificacao"), filtro.listaClassificacao()));
        }
        if (filtro.status() != null) {
            especificacao = especificacao.and((root, query, cb) ->
                    cb.equal(root.get("pessoa").get("status"), filtro.status()));
        }
        // "id" desempata nomes iguais para a paginação ser estável
        PageRequest pageable = PageRequest.of(pagina, tamanho, Sort.by("pessoa.nome").and(Sort.by("id")));
        return coristaRepository.findAll(especificacao, pageable);
    }

    /**
     * Atualiza pessoa e corista. Enquanto não houve promoção antecipada, a lista volta a seguir a idade (ex.: data de
     * nascimento corrigida); depois da promoção, a lista JOVEM é permanente.
     */
    @Transactional
    public Corista atualizar(UUID id, CoristaRequest request) {
        Corista corista = buscarPorId(id);
        Pessoa pessoa = pessoaService.atualizar(corista.getPessoa().getId(), request.pessoa());
        preencher(corista, request);
        if (corista.getPromovidoEm() == null) {
            corista.setListaClassificacao(listaPelaIdade(pessoa));
        }
        return coristaRepository.save(corista);
    }

    /** Soft delete: o cadastro fica {@code INATIVO}; o corista permanece no banco. */
    @Transactional
    public void inativar(UUID id) {
        Corista corista = buscarPorId(id);
        pessoaService.inativar(corista.getPessoa().getId());
    }

    /** Aprovação do cadastro ({@code PENDENTE -> APROVADO}). Ainda sem endpoint: depende de autenticação. */
    @Transactional
    public Corista aprovar(UUID id, UUID aprovadoPor) {
        Corista corista = buscarPorId(id);
        pessoaService.aprovar(corista.getPessoa().getId(), aprovadoPor);
        return corista;
    }

    /**
     * Promoção antecipada ADOLESCENTE -> JOVEM: só nesse sentido, permanente, com trilha. Vale apenas para quem
     * ainda é adolescente pela idade; a pessoa continua menor (proteções LGPD seguem valendo). Ainda sem endpoint:
     * só o {@code LIDER_MOCIDADE} pode promover, e isso depende de autenticação.
     */
    @Transactional
    public Corista promover(UUID id, UUID promovidoPor) {
        Corista corista = buscarPorId(id);
        if (corista.getListaClassificacao() == ListaClassificacao.JOVEM) {
            throw new PromocaoInvalidaException("o corista já está na lista de jovens.");
        }
        if (!corista.getPessoa().menorDeIdade(LocalDate.now(clock))) {
            throw new PromocaoInvalidaException("o corista já tem 18 anos ou mais, a promoção só vale antes da idade.");
        }
        corista.setListaClassificacao(ListaClassificacao.JOVEM);
        corista.setPromovidoPor(promovidoPor);
        corista.setPromovidoEm(Instant.now(clock));
        return coristaRepository.save(corista);
    }

    private void preencher(Corista corista, CoristaRequest request) {
        corista.setTipoVoz(request.tipoVoz());
        corista.setTamanhoCamisa(request.tamanhoCamisa());
        corista.setOcupacao(textoOpcional(request.ocupacao()));
    }

    private ListaClassificacao listaPelaIdade(Pessoa pessoa) {
        return switch (pessoa.faixaEtaria(LocalDate.now(clock))) {
            case ADOLESCENTE -> ListaClassificacao.ADOLESCENTE;
            case JOVEM -> ListaClassificacao.JOVEM;
        };
    }

    private String textoOpcional(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.trim();
    }

    // % e _ digitados pelo usuário não podem virar curingas do LIKE
    private String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
