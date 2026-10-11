package com.registraai.registro_coristas_api.congregacao.service;

import com.registraai.registro_coristas_api.agrupamento.exception.SubdivisaoInativaException;
import com.registraai.registro_coristas_api.agrupamento.model.Subdivisao;
import com.registraai.registro_coristas_api.agrupamento.service.SubdivisaoService;
import com.registraai.registro_coristas_api.congregacao.dto.CongregacaoRequest;
import com.registraai.registro_coristas_api.congregacao.exception.CongregacaoNaoEncontradaException;
import com.registraai.registro_coristas_api.congregacao.exception.CongregacaoNomeDuplicadoException;
import com.registraai.registro_coristas_api.congregacao.model.Congregacao;
import com.registraai.registro_coristas_api.congregacao.repository.CongregacaoRepository;
import com.registraai.registro_coristas_api.endereco.service.EnderecoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CongregacaoService {

    private final CongregacaoRepository congregacaoRepository;
    private final SubdivisaoService subdivisaoService;
    private final EnderecoService enderecoService;

    @Transactional
    public Congregacao criar(CongregacaoRequest request) {
        Subdivisao subdivisao = subdivisaoService.buscarAtivaParaVinculo(request.subdivisaoId());
        String nome = request.nome().trim();
        validarNomeDisponivel(subdivisao, nome, null);

        Congregacao congregacao = new Congregacao();
        congregacao.setSubdivisao(subdivisao);
        congregacao.setNome(nome);
        if (request.endereco() != null) {
            congregacao.setEndereco(enderecoService.criar(request.endereco()));
        }
        return congregacaoRepository.save(congregacao);
    }

    @Transactional(readOnly = true)
    public Congregacao buscarPorId(UUID id) {
        return congregacaoRepository.findById(id)
                .orElseThrow(() -> new CongregacaoNaoEncontradaException(id));
    }

    /**
     * @param agrupamentoId {@code null} não filtra por agrupamento. @param subdivisaoId {@code null} não filtra por
     *                      subdivisão. @param ativa {@code null} não filtra por situação.
     */
    @Transactional(readOnly = true)
    public List<Congregacao> listar(UUID agrupamentoId, UUID subdivisaoId, Boolean ativa) {
        Specification<Congregacao> filtro = Specification.unrestricted();
        if (agrupamentoId != null) {
            filtro = filtro.and((root, query, cb) ->
                    cb.equal(root.get("subdivisao").get("agrupamento").get("id"), agrupamentoId));
        }
        if (subdivisaoId != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("subdivisao").get("id"), subdivisaoId));
        }
        if (ativa != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("ativa"), ativa));
        }
        return congregacaoRepository.findAll(filtro, Sort.by("nome"));
    }

    /**
     * Atualiza a congregação. Se {@code subdivisaoId} mudar, é um remanejamento: a subdivisão de destino precisa
     * estar ativa. O endereço só é alterado quando enviado (atualiza o existente ou cria um, se ainda não houver).
     */
    @Transactional
    public Congregacao atualizar(UUID id, CongregacaoRequest request) {
        Congregacao congregacao = buscarPorId(id);
        boolean mesmaSubdivisao = congregacao.getSubdivisao().getId().equals(request.subdivisaoId());
        // só exige subdivisão ativa quando está indo para outra (senão renomear congregação inativa em subdivisão inativa falharia)
        Subdivisao subdivisao = mesmaSubdivisao
                ? congregacao.getSubdivisao()
                : subdivisaoService.buscarAtivaParaVinculo(request.subdivisaoId());
        String nome = request.nome().trim();
        validarNomeDisponivel(subdivisao, nome, id);

        congregacao.setSubdivisao(subdivisao);
        congregacao.setNome(nome);
        if (request.endereco() != null) {
            if (congregacao.getEndereco() == null) {
                congregacao.setEndereco(enderecoService.criar(request.endereco()));
            } else {
                enderecoService.atualizar(congregacao.getEndereco().getId(), request.endereco());
            }
        }
        return congregacaoRepository.save(congregacao);
    }

    /** Soft delete: a congregação permanece no banco com {@code ativa = false}. Idempotente. */
    @Transactional
    public void inativar(UUID id) {
        Congregacao congregacao = buscarPorId(id);
        congregacao.setAtiva(false);
        congregacaoRepository.save(congregacao);
    }

    /** Só reativa se a subdivisão e o agrupamento dela estiverem ativos (inativo não pode ter congregação ativa). */
    @Transactional
    public Congregacao reativar(UUID id) {
        Congregacao congregacao = buscarPorId(id);
        Subdivisao subdivisao = congregacao.getSubdivisao();
        if (!subdivisao.isAtivo() || !subdivisao.getAgrupamento().isAtivo()) {
            throw new SubdivisaoInativaException();
        }
        congregacao.setAtiva(true);
        return congregacaoRepository.save(congregacao);
    }

    /** @param idIgnorado congregação em edição (não conta como duplicada de si mesma); {@code null} na criação. */
    private void validarNomeDisponivel(Subdivisao subdivisao, String nome, UUID idIgnorado) {
        boolean duplicado = idIgnorado == null
                ? congregacaoRepository.existsBySubdivisaoIdAndNomeIgnoreCase(subdivisao.getId(), nome)
                : congregacaoRepository.existsBySubdivisaoIdAndNomeIgnoreCaseAndIdNot(subdivisao.getId(), nome, idIgnorado);
        if (duplicado) {
            throw new CongregacaoNomeDuplicadoException(nome);
        }
    }
}
