package com.registraai.registro_coristas_api.agrupamento.dto;

import com.registraai.registro_coristas_api.agrupamento.model.Subdivisao;

import java.time.Instant;
import java.util.UUID;

public record SubdivisaoResponse(
        UUID id,
        UUID agrupamentoId,
        Integer numero,
        String nome,
        boolean ativo,
        Instant criadoEm,
        Instant atualizadoEm
) {

    public static SubdivisaoResponse de(Subdivisao subdivisao) {
        return new SubdivisaoResponse(
                subdivisao.getId(),
                subdivisao.getAgrupamento().getId(),
                subdivisao.getNumero(),
                subdivisao.getNome(),
                subdivisao.isAtivo(),
                subdivisao.getCriadoEm(),
                subdivisao.getAtualizadoEm()
        );
    }
}
