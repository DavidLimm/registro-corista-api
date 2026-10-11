package com.registraai.registro_coristas_api.autenticacao.dto;

import com.registraai.registro_coristas_api.usuario.validation.SenhaForte;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmacaoRedefinicaoRequest(

        @NotBlank
        String token,

        @NotBlank @SenhaForte @Size(max = 100)
        String novaSenha
) {
}
