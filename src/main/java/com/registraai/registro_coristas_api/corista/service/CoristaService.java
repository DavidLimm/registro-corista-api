package com.registraai.registro_coristas_api.corista.service;

import com.registraai.registro_coristas_api.autenticacao.service.UsuarioLogado;
import com.registraai.registro_coristas_api.corista.dto.CoristaFiltro;
import com.registraai.registro_coristas_api.corista.dto.CoristaRequest;
import com.registraai.registro_coristas_api.corista.exception.CorrecaoDeNascimentoNegadaException;
import com.registraai.registro_coristas_api.corista.exception.CoristaNaoEncontradoException;
import com.registraai.registro_coristas_api.corista.exception.PromocaoInvalidaException;
import com.registraai.registro_coristas_api.corista.model.Corista;
import com.registraai.registro_coristas_api.corista.model.ListaClassificacao;
import com.registraai.registro_coristas_api.corista.model.TipoVoz;
import com.registraai.registro_coristas_api.corista.repository.CoristaRepository;
import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.pessoa.service.PessoaService;
import com.registraai.registro_coristas_api.role.model.Permissao;
import com.registraai.registro_coristas_api.usuario.model.Usuario;
import com.registraai.registro_coristas_api.usuario.service.UsuarioService;
import com.registraai.registro_coristas_api.usuario.service.EscopoDeAcesso;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CoristaService {

    private final CoristaRepository coristaRepository;
    private final PessoaService pessoaService;
    private final UsuarioService usuarioService;
    private final UsuarioLogado usuarioLogado;
    private final EscopoDeAcesso escopo;
    private final Clock clock;

    /**
     * Grava pessoa e corista na mesma transação. A lista de classificação nasce conforme a idade. Se
     * {@code request.usuario()} vier preenchido (auto-cadastro), o login também é criado na mesma transação, com o
     * papel derivado da lista de classificação — nunca escolhido pelo cliente.
     */
    @Transactional
    public Corista criar(CoristaRequest request) {
        Pessoa pessoa = pessoaService.criar(request.pessoa());
        Corista corista = new Corista();
        corista.setPessoa(pessoa);
        preencher(corista, request);
        ListaClassificacao lista = listaPelaIdade(pessoa);
        corista.setListaClassificacao(lista);
        Corista salvo = coristaRepository.save(corista);
        if (request.usuario() != null) {
            String nomeDoRole = lista == ListaClassificacao.ADOLESCENTE ? "CORISTA_ADOLESCENTES" : "CORISTA_JOVENS";
            usuarioService.criarComPapelUnico(pessoa, request.usuario().email(), request.usuario().senha(), nomeDoRole);
        }
        return salvo;
    }

    /**
     * Só devolve quem o usuário logado pode acessar: o próprio cadastro ou alguém da lista do seu recorte. Fora disso
     * responde "não encontrado" (404), para não revelar que o cadastro existe. Todas as operações abaixo passam por aqui.
     */
    @Transactional(readOnly = true)
    public Corista buscarPorId(UUID id) {
        Corista corista = coristaRepository.findById(id)
                .orElseThrow(() -> new CoristaNaoEncontradoException(id));
        if (corista.desligado() || !podeAcessar(corista)) {
            throw new CoristaNaoEncontradoException(id);
        }
        return corista;
    }

    private boolean podeAcessar(Corista corista) {
        return usuarioLogado.atual()
                .map(usuario -> ehOProprioCadastro(usuario, corista) || estaNoRecorte(usuario, corista))
                .orElse(false);
    }

    // só vale para quem tem papel de corista: líder não é corista, então o cadastro antigo dele fica bloqueado
    private boolean ehOProprioCadastro(Usuario usuario, Corista corista) {
        return RecorteDeAcesso.ehCorista(usuario)
                && usuario.getPessoa().getId().equals(corista.getPessoa().getId());
    }

    private boolean estaNoRecorte(Usuario usuario, Corista corista) {
        boolean listaPermitida = RecorteDeAcesso.listasPermitidas(usuario).contains(corista.getListaClassificacao());
        boolean congregacaoPermitida = RecorteDeAcesso.congregacaoRestrita(usuario)
                .map(congregacaoId -> congregacaoId.equals(corista.getPessoa().getCongregacao().getId()))
                .orElse(true);
        return listaPermitida && congregacaoPermitida && !escopo.fora(corista.getPessoa());
    }

    /** O corista da pessoa logada; 404 se quem está logado não é corista (ex.: um líder). */
    @Transactional(readOnly = true)
    public Corista buscarDoUsuarioLogado() {
        UUID pessoaId = pessoaDoUsuarioLogado();
        if (!usuarioLogado.atual().map(RecorteDeAcesso::ehCorista).orElse(false)) {
            throw new CoristaNaoEncontradoException(pessoaId);
        }
        return coristaRepository.findByPessoaId(pessoaId)
                .filter(corista -> !corista.desligado())
                .orElseThrow(() -> new CoristaNaoEncontradoException(pessoaId));
    }

    /**
     * O corista {@code id} é o do próprio usuário logado? Usado nas regras de {@code @PreAuthorize}: quem não tem
     * permissão de leitura geral ({@code CORISTA_LER}, a liderança) só enxerga e edita o próprio cadastro.
     */
    @Transactional(readOnly = true)
    public boolean ehProprio(UUID id) {
        return usuarioLogado.atual()
                .map(usuario -> RecorteDeAcesso.ehCorista(usuario)
                        && coristaRepository.existsByIdAndPessoaIdAndDesligadoEmIsNull(id, usuario.getPessoa().getId()))
                .orElse(false);
    }

    private UUID pessoaDoUsuarioLogado() {
        return usuarioLogado.atual()
                .map(usuario -> usuario.getPessoa().getId())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Nenhum usuário autenticado."));
    }

    /** Ordenada por nome (o cliente não escolhe a ordenação). Só traz as listas do recorte do usuário logado. */
    @Transactional(readOnly = true)
    public Page<Corista> listar(CoristaFiltro filtro, int pagina, int tamanho) {
        PageRequest pageable = PageRequest.of(pagina, tamanho, Sort.by("pessoa.nome").and(Sort.by("id")));
        Set<ListaClassificacao> permitidas = usuarioLogado.atual()
                .map(RecorteDeAcesso::listasPermitidas)
                .orElse(Set.of());
        if (permitidas.isEmpty()) {
            return Page.empty(pageable);
        }
        // desligado (ex.: virou líder) não aparece mais; só a lista do recorte do usuário
        Specification<Corista> especificacao = (root, query, cb) -> cb.and(
                cb.isNull(root.get("desligadoEm")), root.get("listaClassificacao").in(permitidas));
        // e só os agrupamentos a que o usuário tem acesso (ADMIN e PASTOR_ADMIN enxergam todos)
        especificacao = especificacao.and(escopo.restringirA(
                root -> root.get("pessoa").get("congregacao").get("subdivisao").get("agrupamento").get("id")));
        Optional<UUID> congregacaoRestrita = usuarioLogado.atual().flatMap(RecorteDeAcesso::congregacaoRestrita);
        if (congregacaoRestrita.isPresent()) {
            UUID congregacaoId = congregacaoRestrita.get();
            especificacao = especificacao.and((root, query, cb) ->
                    cb.equal(root.get("pessoa").get("congregacao").get("id"), congregacaoId));
        }
        if (filtro.nome() != null && !filtro.nome().isBlank()) {
            String trecho = "%" + escaparLike(filtro.nome().trim().toLowerCase()) + "%";
            especificacao = especificacao.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("pessoa").get("nome")), trecho, '\\'));
        }
        if (filtro.agrupamentoId() != null) {
            especificacao = especificacao.and((root, query, cb) ->
                    cb.equal(root.get("pessoa").get("congregacao").get("subdivisao").get("agrupamento").get("id"), filtro.agrupamentoId()));
        }
        if (filtro.subdivisaoId() != null) {
            especificacao = especificacao.and((root, query, cb) ->
                    cb.equal(root.get("pessoa").get("congregacao").get("subdivisao").get("id"), filtro.subdivisaoId()));
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
        // "id" (na ordenação acima) desempata nomes iguais para a paginação ser estável
        return coristaRepository.findAll(especificacao, pageable);
    }

    /**
     * Atualiza pessoa e corista. Enquanto não houve promoção antecipada, a lista volta a seguir a idade (ex.: data de
     * nascimento corrigida); depois da promoção, a lista JOVEM é permanente.
     */
    @Transactional
    public Corista atualizar(UUID id, CoristaRequest request) {
        Corista corista = buscarPorId(id);
        if (!corista.getPessoa().getDataNascimento().equals(request.pessoa().dataNascimento())
                && !usuarioLogado.temPermissao(Permissao.CORISTA_CORRIGIR_NASCIMENTO.name())) {
            throw new CorrecaoDeNascimentoNegadaException();
        }
        Pessoa pessoa = pessoaService.atualizar(corista.getPessoa().getId(), request.pessoa());
        preencher(corista, request);
        if (corista.getPromovidoEm() == null) {
            corista.setListaClassificacao(listaPelaIdade(pessoa));
        }
        return coristaRepository.save(corista);
    }

    /** Troca só o tipo de voz (o que o maestro pode editar). A lista de classificação não muda. */
    @Transactional
    public Corista atualizarVoz(UUID id, TipoVoz tipoVoz) {
        Corista corista = buscarPorId(id);
        corista.setTipoVoz(tipoVoz);
        return coristaRepository.save(corista);
    }

    /** Soft delete: o cadastro fica {@code INATIVO}; o corista permanece no banco. */
    @Transactional
    public void inativar(UUID id) {
        Corista corista = buscarPorId(id);
        pessoaService.inativar(corista.getPessoa().getId());
    }

    /** Aprovação do cadastro ({@code PENDENTE -> APROVADO}). */
    @Transactional
    public Corista aprovar(UUID id, UUID aprovadoPor) {
        Corista corista = buscarPorId(id);
        pessoaService.aprovar(corista.getPessoa().getId(), aprovadoPor);
        return corista;
    }

    /** Reprovação do cadastro ({@code PENDENTE -> REPROVADO}). Nunca muda {@code listaClassificacao}. */
    @Transactional
    public Corista reprovar(UUID id, UUID reprovadoPor) {
        Corista corista = buscarPorId(id);
        pessoaService.reprovar(corista.getPessoa().getId(), reprovadoPor);
        return corista;
    }

    /** {@code APROVADO -> AFASTADO}. O status fica na pessoa, então a regra é a mesma de {@code PessoaService.afastar}. */
    @Transactional
    public Corista afastar(UUID id, UUID afastadoPor) {
        Corista corista = buscarPorId(id);
        pessoaService.afastar(corista.getPessoa().getId(), afastadoPor);
        return corista;
    }

    /** {@code AFASTADO -> APROVADO}. Mesma regra de {@link #afastar}. */
    @Transactional
    public Corista retornarDoAfastamento(UUID id, UUID retornadoPor) {
        Corista corista = buscarPorId(id);
        pessoaService.retornarDoAfastamento(corista.getPessoa().getId(), retornadoPor);
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
