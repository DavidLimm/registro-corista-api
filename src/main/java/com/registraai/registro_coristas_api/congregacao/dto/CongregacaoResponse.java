package com.registraai.registro_coristas_api.congregacao.dto;

import com.registraai.registro_coristas_api.area.dto.AreaResponse;
import com.registraai.registro_coristas_api.congregacao.model.Congregacao;
import com.registraai.registro_coristas_api.endereco.dto.EnderecoResponse;

import java.time.Instant;
import java.util.UUID;

public record CongregacaoResponse(
        UUID id,
        AreaResponse area,
        String nome,
        boolean ativa,
        EnderecoResponse endereco,
        Instant criadoEm,
        Instant atualizadoEm
) {

    public static CongregacaoResponse de(Congregacao congregacao) {
        return new CongregacaoResponse(
                congregacao.getId(),
                AreaResponse.de(congregacao.getArea()),
                congregacao.getNome(),
                congregacao.isAtiva(),
                congregacao.getEndereco() == null ? null : EnderecoResponse.de(congregacao.getEndereco()),
                congregacao.getCriadoEm(),
                congregacao.getAtualizadoEm()
        );
    }
}
