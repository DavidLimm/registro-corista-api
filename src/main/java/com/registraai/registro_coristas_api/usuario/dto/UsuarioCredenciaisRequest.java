package com.registraai.registro_coristas_api.usuario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credenciais informadas no auto-cadastro (ex.: junto com {@code CoristaRequest}). Só e-mail e senha — o papel
 * (role) nunca vem do cliente aqui: quem orquestra a criação deriva o papel certo (ex.: pela lista de
 * classificação do corista), pra ninguém se auto-atribuir um papel de maior privilégio no próprio cadastro.
 */
public record UsuarioCredenciaisRequest(

        @NotBlank @Email @Size(max = 150)
        String email,

        @NotBlank @Size(min = 8, max = 100)
        String senha
) {
}
