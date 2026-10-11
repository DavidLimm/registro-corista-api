package com.registraai.registro_coristas_api.role.dto;

import com.registraai.registro_coristas_api.role.model.Role;
import com.registraai.registro_coristas_api.role.model.StatusRole;

import java.time.Instant;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String nome,
        String descricao,
        boolean ativo,
        StatusRole status,
        UUID solicitadoPor,
        UUID decididoPor,
        Instant decididoEm,
        Instant criadoEm,
        Instant atualizadoEm
) {

    public static RoleResponse de(Role role) {
        return new RoleResponse(
                role.getId(),
                role.getNome(),
                role.getDescricao(),
                role.isAtivo(),
                role.getStatus(),
                role.getSolicitadoPor(),
                role.getDecididoPor(),
                role.getDecididoEm(),
                role.getCriadoEm(),
                role.getAtualizadoEm()
        );
    }
}
