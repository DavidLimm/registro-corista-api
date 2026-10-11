package com.registraai.registro_coristas_api.congregacao.dto;

import com.registraai.registro_coristas_api.agrupamento.dto.AgrupamentoResponse;
import com.registraai.registro_coristas_api.agrupamento.dto.SubdivisaoResponse;
import com.registraai.registro_coristas_api.congregacao.model.Congregacao;
import com.registraai.registro_coristas_api.endereco.dto.EnderecoResponse;

import java.time.Instant;
import java.util.UUID;

public record CongregacaoResponse(
        UUID id,
        AgrupamentoResponse agrupamento,
        SubdivisaoResponse subdivisao,
        String nome,
        boolean ativa,
        EnderecoResponse endereco,
        Instant criadoEm,
        Instant atualizadoEm
) {

    public static CongregacaoResponse de(Congregacao congregacao) {
        return new CongregacaoResponse(
                congregacao.getId(),
                AgrupamentoResponse.de(congregacao.getSubdivisao().getAgrupamento()),
                SubdivisaoResponse.de(congregacao.getSubdivisao()),
                congregacao.getNome(),
                congregacao.isAtiva(),
                congregacao.getEndereco() == null ? null : EnderecoResponse.de(congregacao.getEndereco()),
                congregacao.getCriadoEm(),
                congregacao.getAtualizadoEm()
        );
    }
}
