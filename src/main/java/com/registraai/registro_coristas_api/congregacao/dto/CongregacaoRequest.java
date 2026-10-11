package com.registraai.registro_coristas_api.congregacao.dto;

import com.registraai.registro_coristas_api.endereco.dto.EnderecoRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Usado no cadastro e na edição. A congregação pertence a uma subdivisão (o agrupamento vem por ela);
 * alterar {@code subdivisaoId} na edição remaneja a congregação (a subdivisão de destino precisa estar ativa).
 * {@code endereco} é opcional: na edição, se ausente, o endereço atual é mantido.
 */
public record CongregacaoRequest(

        @NotNull
        UUID subdivisaoId,

        @NotBlank @Size(max = 150)
        String nome,

        @Valid
        EnderecoRequest endereco
) {
}
