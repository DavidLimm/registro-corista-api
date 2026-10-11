package com.registraai.registro_coristas_api.agrupamento.dto;

import com.registraai.registro_coristas_api.agrupamento.model.Agrupamento;
import com.registraai.registro_coristas_api.agrupamento.model.TipoAgrupamento;

import java.time.Instant;
import java.util.UUID;

public record AgrupamentoResponse(
        UUID id,
        TipoAgrupamento tipo,
        String nome,
        Integer numero,
        String pais,
        boolean ativo,
        Instant criadoEm,
        Instant atualizadoEm
) {

    public static AgrupamentoResponse de(Agrupamento agrupamento) {
        return new AgrupamentoResponse(
                agrupamento.getId(),
                agrupamento.getTipo(),
                agrupamento.getNome(),
                agrupamento.getNumero(),
                agrupamento.getPais(),
                agrupamento.isAtivo(),
                agrupamento.getCriadoEm(),
                agrupamento.getAtualizadoEm()
        );
    }
}
