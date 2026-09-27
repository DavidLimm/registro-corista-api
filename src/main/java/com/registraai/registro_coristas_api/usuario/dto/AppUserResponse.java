package com.registraai.registro_coristas_api.usuario.dto;

import com.registraai.registro_coristas_api.role.model.Role;
import com.registraai.registro_coristas_api.usuario.model.AppUser;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/** Nunca expõe {@code senhaHash}. */
public record AppUserResponse(
        UUID id,
        UUID pessoaId,
        String pessoaNome,
        String email,
        boolean ativo,
        Set<String> roles,
        Instant criadoEm,
        Instant atualizadoEm
) {

    public static AppUserResponse de(AppUser appUser) {
        return new AppUserResponse(
                appUser.getId(),
                appUser.getPessoa().getId(),
                appUser.getPessoa().getNome(),
                appUser.getEmail(),
                appUser.isAtivo(),
                appUser.getRoles().stream().map(Role::getNome).collect(Collectors.toCollection(TreeSet::new)),
                appUser.getCriadoEm(),
                appUser.getAtualizadoEm()
        );
    }
}
