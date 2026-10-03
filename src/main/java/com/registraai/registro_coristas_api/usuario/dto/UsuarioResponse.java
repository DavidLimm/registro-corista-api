package com.registraai.registro_coristas_api.usuario.dto;

import com.registraai.registro_coristas_api.role.model.Role;
import com.registraai.registro_coristas_api.usuario.model.Usuario;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/** Nunca expõe {@code senhaHash}. */
public record UsuarioResponse(
        UUID id,
        UUID pessoaId,
        String pessoaNome,
        String email,
        boolean ativo,
        Set<String> roles,
        Instant criadoEm,
        Instant atualizadoEm
) {

    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getPessoa().getId(),
                usuario.getPessoa().getNome(),
                usuario.getEmail(),
                usuario.isAtivo(),
                usuario.getRoles().stream().map(Role::getNome).collect(Collectors.toCollection(TreeSet::new)),
                usuario.getCriadoEm(),
                usuario.getAtualizadoEm()
        );
    }
}
