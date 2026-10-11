package com.registraai.registro_coristas_api.autenticacao.dto;

import com.registraai.registro_coristas_api.usuario.validation.SenhaForte;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrocaSenhaRequest(

        @NotBlank
        String senhaAtual,

        @NotBlank @SenhaForte @Size(max = 100)
        String novaSenha
) {
}
