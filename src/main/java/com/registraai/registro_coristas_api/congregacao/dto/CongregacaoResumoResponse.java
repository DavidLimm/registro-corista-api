package com.registraai.registro_coristas_api.congregacao.dto;

import com.registraai.registro_coristas_api.agrupamento.dto.AgrupamentoResponse;
import com.registraai.registro_coristas_api.agrupamento.dto.SubdivisaoResponse;
import com.registraai.registro_coristas_api.congregacao.model.Congregacao;

import java.util.UUID;

/** Congregação sem endereço nem timestamps: usada quando ela aparece aninhada em outro recurso (ex.: pessoa). */
public record CongregacaoResumoResponse(
        UUID id,
        String nome,
        boolean ativa,
        AgrupamentoResponse agrupamento,
        SubdivisaoResponse subdivisao
) {

    public static CongregacaoResumoResponse de(Congregacao congregacao) {
        return new CongregacaoResumoResponse(
                congregacao.getId(),
                congregacao.getNome(),
                congregacao.isAtiva(),
                AgrupamentoResponse.de(congregacao.getSubdivisao().getAgrupamento()),
                SubdivisaoResponse.de(congregacao.getSubdivisao())
        );
    }
}
