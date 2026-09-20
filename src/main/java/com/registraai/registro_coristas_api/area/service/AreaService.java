package com.registraai.registro_coristas_api.area.service;

import com.registraai.registro_coristas_api.area.dto.AreaRequest;
import com.registraai.registro_coristas_api.area.exception.AreaNaoEncontradaException;
import com.registraai.registro_coristas_api.area.exception.AreaNumeroDuplicadoException;
import com.registraai.registro_coristas_api.area.model.Area;
import com.registraai.registro_coristas_api.area.repository.AreaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AreaService {

    private final AreaRepository areaRepository;

    @Transactional
    public Area criar(AreaRequest request) {
        if (areaRepository.existsByNumero(request.numero())) {
            throw new AreaNumeroDuplicadoException(request.numero());
        }
        Area area = new Area();
        preencher(area, request);
        return areaRepository.save(area);
    }

    @Transactional(readOnly = true)
    public Area buscarPorId(UUID id) {
        return areaRepository.findById(id)
                .orElseThrow(() -> new AreaNaoEncontradaException(id));
    }

    /** @param ativa {@code null} lista todas; {@code true}/{@code false} filtra por situação. */
    @Transactional(readOnly = true)
    public List<Area> listar(Boolean ativa) {
        if (ativa == null) {
            return areaRepository.findAllByOrderByNumeroAsc();
        }
        return areaRepository.findAllByAtivaOrderByNumeroAsc(ativa);
    }

    @Transactional
    public Area atualizar(UUID id, AreaRequest request) {
        Area area = buscarPorId(id);
        if (areaRepository.existsByNumeroAndIdNot(request.numero(), id)) {
            throw new AreaNumeroDuplicadoException(request.numero());
        }
        preencher(area, request);
        return areaRepository.save(area);
    }

    /** Soft delete: a área permanece no banco com {@code ativa = false}. Idempotente. */
    @Transactional
    public void inativar(UUID id) {
        Area area = buscarPorId(id);
        area.setAtiva(false);
        areaRepository.save(area);
    }

    @Transactional
    public Area reativar(UUID id) {
        Area area = buscarPorId(id);
        area.setAtiva(true);
        return areaRepository.save(area);
    }

    private void preencher(Area area, AreaRequest request) {
        area.setNumero(request.numero());
        area.setNome(request.nome().trim());
    }
}
