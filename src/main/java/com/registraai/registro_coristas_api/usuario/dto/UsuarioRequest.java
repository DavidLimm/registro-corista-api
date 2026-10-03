package com.registraai.registro_coristas_api.usuario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/**
 * Cadastro e edição em uma única requisição (PUT substitui tudo, inclusive a senha — não há endpoint parcial de
 * "trocar senha" ainda). A pessoa vinculada não pode mudar depois de criada: o service rejeita um {@code pessoaId}
 * diferente do já gravado.
 */
public record UsuarioRequest(

        @NotNull
        UUID pessoaId,

        @NotNull @Email @Size(max = 150)
        String email,

        @NotNull @Size(min = 8, max = 100)
        String senha,

        @NotEmpty
        Set<@NotNull UUID> roleIds
) {
}
