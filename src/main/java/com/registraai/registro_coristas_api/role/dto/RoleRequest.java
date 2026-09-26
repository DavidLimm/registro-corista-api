package com.registraai.registro_coristas_api.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoleRequest(

        @NotBlank @Size(max = 50)
        String nome,

        @NotBlank @Size(max = 255)
        String descricao
) {
}
