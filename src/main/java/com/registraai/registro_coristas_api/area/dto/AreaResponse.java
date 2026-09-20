package com.registraai.registro_coristas_api.area.dto;

import com.registraai.registro_coristas_api.area.model.Area;

import java.time.Instant;
import java.util.UUID;

public record AreaResponse(
        UUID id,
        Integer numero,
        String nome,
        boolean ativa,
        Instant criadoEm,
        Instant atualizadoEm
) {

    public static AreaResponse de(Area area) {
        return new AreaResponse(
                area.getId(),
                area.getNumero(),
                area.getNome(),
                area.isAtiva(),
                area.getCriadoEm(),
                area.getAtualizadoEm()
        );
    }
}
