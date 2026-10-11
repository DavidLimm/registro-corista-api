package com.registraai.registro_coristas_api.afastamento.service;

import com.registraai.registro_coristas_api.afastamento.exception.SolicitacaoAfastamentoInvalidaException;
import com.registraai.registro_coristas_api.afastamento.exception.SolicitacaoAfastamentoNaoEncontradaException;
import com.registraai.registro_coristas_api.afastamento.model.SolicitacaoAfastamento;
import com.registraai.registro_coristas_api.afastamento.model.StatusSolicitacao;
import com.registraai.registro_coristas_api.afastamento.repository.SolicitacaoAfastamentoRepository;
import com.registraai.registro_coristas_api.corista.model.Corista;
import com.registraai.registro_coristas_api.corista.service.CoristaService;
import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.pessoa.model.StatusPessoa;
import com.registraai.registro_coristas_api.pessoa.service.PessoaService;
import com.registraai.registro_coristas_api.usuario.service.EscopoDeAcesso;
import org.springframework.data.jpa.domain.Specification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Pedido de afastamento: quem só tem {@code CORISTA_SOLICITAR_AFASTAMENTO} pede, e quem tem
 * {@code CORISTA_APROVAR_AFASTAMENTO} decide. Aprovar executa o afastamento da pessoa ({@code APROVADO -> AFASTADO}).
 */
@Service
@RequiredArgsConstructor
public class SolicitacaoAfastamentoService {

    private final SolicitacaoAfastamentoRepository solicitacaoRepository;
    private final CoristaService coristaService;
    private final PessoaService pessoaService;
    private final EscopoDeAcesso escopo;
    private final Clock clock;

    /**
     * Pede o afastamento de um corista. Só vale para quem enxerga o corista (recorte de acesso, 404 fora dele), só se a
     * pessoa está {@code APROVADO}, e só uma solicitação pendente por vez.
     */
    @Transactional
    public SolicitacaoAfastamento solicitar(UUID coristaId, String motivo, UUID solicitadoPor) {
        Corista corista = coristaService.buscarPorId(coristaId);
        Pessoa pessoa = corista.getPessoa();
        if (pessoa.getStatus() != StatusPessoa.APROVADO) {
            throw new SolicitacaoAfastamentoInvalidaException("só quem está aprovado pode ser afastado.");
        }
        if (solicitacaoRepository.existsByPessoaIdAndStatus(pessoa.getId(), StatusSolicitacao.PENDENTE)) {
            throw new SolicitacaoAfastamentoInvalidaException("já existe um pedido de afastamento pendente para esta pessoa.");
        }
        return solicitacaoRepository.save(SolicitacaoAfastamento.builder()
                .pessoa(pessoa)
                .motivo(motivo.trim())
                .solicitadoPor(solicitadoPor)
                .build());
    }

    /** Mais antigas primeiro (a fila de quem decide). {@code status} nulo traz todas. */
    @Transactional(readOnly = true)
    public Page<SolicitacaoAfastamento> listar(StatusSolicitacao status, int pagina, int tamanho) {
        PageRequest pageable = PageRequest.of(pagina, tamanho, Sort.by("solicitadoEm").and(Sort.by("id")));
        // só os pedidos de pessoas dos agrupamentos do usuário logado
        Specification<SolicitacaoAfastamento> filtro = escopo.restringirA(
                root -> root.get("pessoa").get("congregacao").get("subdivisao").get("agrupamento").get("id"));
        if (status != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        return solicitacaoRepository.findAll(filtro, pageable);
    }

    /** {@code PENDENTE -> APROVADA}: afasta a pessoa (registrando quem aprovou) e fecha o pedido. */
    @Transactional
    public SolicitacaoAfastamento aprovar(UUID id, UUID aprovadoPor) {
        SolicitacaoAfastamento solicitacao = pendente(id);
        pessoaService.afastar(solicitacao.getPessoa().getId(), aprovadoPor);
        return decidir(solicitacao, StatusSolicitacao.APROVADA, aprovadoPor);
    }

    /** {@code PENDENTE -> NEGADA}: a pessoa continua como está. */
    @Transactional
    public SolicitacaoAfastamento negar(UUID id, UUID negadoPor) {
        return decidir(pendente(id), StatusSolicitacao.NEGADA, negadoPor);
    }

    private SolicitacaoAfastamento pendente(UUID id) {
        SolicitacaoAfastamento solicitacao = solicitacaoRepository.findById(id)
                .orElseThrow(() -> new SolicitacaoAfastamentoNaoEncontradaException(id));
        // pedido de pessoa fora do escopo: 404
        if (escopo.fora(solicitacao.getPessoa())) {
            throw new SolicitacaoAfastamentoNaoEncontradaException(id);
        }
        if (solicitacao.getStatus() != StatusSolicitacao.PENDENTE) {
            throw new SolicitacaoAfastamentoInvalidaException("este pedido já foi decidido.");
        }
        return solicitacao;
    }

    private SolicitacaoAfastamento decidir(SolicitacaoAfastamento solicitacao, StatusSolicitacao status, UUID decididoPor) {
        solicitacao.setStatus(status);
        solicitacao.setDecididoPor(decididoPor);
        solicitacao.setDecididoEm(Instant.now(clock));
        return solicitacaoRepository.save(solicitacao);
    }
}
