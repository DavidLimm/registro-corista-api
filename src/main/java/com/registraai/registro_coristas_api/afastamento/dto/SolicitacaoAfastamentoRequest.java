package com.registraai.registro_coristas_api.afastamento.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SolicitacaoAfastamentoRequest(

        @NotBlank @Size(max = 500)
        String motivo
) {
}
