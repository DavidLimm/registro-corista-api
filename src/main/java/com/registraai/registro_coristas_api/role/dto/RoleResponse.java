package com.registraai.registro_coristas_api.role.dto;

import com.registraai.registro_coristas_api.role.model.Role;

import java.time.Instant;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String nome,
        String descricao,
        boolean ativo,
        Instant criadoEm,
        Instant atualizadoEm
) {

    public static RoleResponse de(Role role) {
        return new RoleResponse(
                role.getId(),
                role.getNome(),
                role.getDescricao(),
                role.isAtivo(),
                role.getCriadoEm(),
                role.getAtualizadoEm()
        );
    }
}
