package com.registraai.registro_coristas_api.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

/** O conjunto completo de permissões do papel: substitui o que havia (vazio tira todas). */
public record RolePermissoesRequest(

        @NotNull
        Set<@NotBlank String> permissoes
) {
}
