package com.registraai.registro_coristas_api.autenticacao.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Sem {@code @Email}: o formato do e-mail é validado no cadastro. No login, um e-mail mal formado (ou com espaços de
 * copiar e colar) só não acha usuário e cai no mesmo 401 de "e-mail ou senha inválidos".
 */
public record LoginRequest(

        @NotBlank
        String email,

        @NotBlank
        String senha
) {
}
