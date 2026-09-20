package com.registraai.registro_coristas_api.congregacao.service;

import com.registraai.registro_coristas_api.area.exception.AreaInativaException;
import com.registraai.registro_coristas_api.area.model.Area;
import com.registraai.registro_coristas_api.area.service.AreaService;
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
    private final AreaService areaService;
    private final EnderecoService enderecoService;

    @Transactional
    public Congregacao criar(CongregacaoRequest request) {
        Area area = buscarAreaAtiva(request.areaId());
        String nome = request.nome().trim();
        validarNomeDisponivel(area, nome, null);

        Congregacao congregacao = new Congregacao();
        congregacao.setArea(area);
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

    /** @param areaId {@code null} não filtra por área. @param ativa {@code null} não filtra por situação. */
    @Transactional(readOnly = true)
    public List<Congregacao> listar(UUID areaId, Boolean ativa) {
        Specification<Congregacao> filtro = Specification.unrestricted();
        if (areaId != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("area").get("id"), areaId));
        }
        if (ativa != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("ativa"), ativa));
        }
        return congregacaoRepository.findAll(filtro, Sort.by("nome"));
    }

    /**
     * Atualiza a congregação. Se {@code areaId} mudar, é um remanejamento: a área de destino precisa estar ativa.
     * O endereço só é alterado quando enviado (atualiza o existente ou cria um, se ainda não houver).
     */
    @Transactional
    public Congregacao atualizar(UUID id, CongregacaoRequest request) {
        Congregacao congregacao = buscarPorId(id);
        boolean mesmaArea = congregacao.getArea().getId().equals(request.areaId());
        // só exige área ativa quando está indo para uma área (senão renomear congregação inativa em área inativa falharia)
        Area area = mesmaArea ? congregacao.getArea() : buscarAreaAtiva(request.areaId());
        String nome = request.nome().trim();
        validarNomeDisponivel(area, nome, id);

        congregacao.setArea(area);
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

    /** Só reativa se a área da congregação estiver ativa (uma área inativa não pode ter congregação ativa). */
    @Transactional
    public Congregacao reativar(UUID id) {
        Congregacao congregacao = buscarPorId(id);
        if (!congregacao.getArea().isAtiva()) {
            throw new AreaInativaException(congregacao.getArea().getNumero());
        }
        congregacao.setAtiva(true);
        return congregacaoRepository.save(congregacao);
    }

    private Area buscarAreaAtiva(UUID areaId) {
        Area area = areaService.buscarPorId(areaId);
        if (!area.isAtiva()) {
            throw new AreaInativaException(area.getNumero());
        }
        return area;
    }

    /** @param idIgnorado congregação em edição (não conta como duplicada de si mesma); {@code null} na criação. */
    private void validarNomeDisponivel(Area area, String nome, UUID idIgnorado) {
        boolean duplicado = idIgnorado == null
                ? congregacaoRepository.existsByAreaIdAndNomeIgnoreCase(area.getId(), nome)
                : congregacaoRepository.existsByAreaIdAndNomeIgnoreCaseAndIdNot(area.getId(), nome, idIgnorado);
        if (duplicado) {
            throw new CongregacaoNomeDuplicadoException(nome, area.getNumero());
        }
    }
}
