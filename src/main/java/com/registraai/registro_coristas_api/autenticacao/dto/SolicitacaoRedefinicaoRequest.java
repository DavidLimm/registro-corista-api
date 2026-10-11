package com.registraai.registro_coristas_api.autenticacao.dto;

import jakarta.validation.constraints.NotBlank;

public record SolicitacaoRedefinicaoRequest(

        @NotBlank
        String email
) {
}
