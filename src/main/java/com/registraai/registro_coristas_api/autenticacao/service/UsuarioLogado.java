package com.registraai.registro_coristas_api.autenticacao.service;

import com.registraai.registro_coristas_api.usuario.model.Usuario;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Quem está autenticado na requisição atual (o {@code Usuario} carregado pelo filtro JWT). */
@Component
public class UsuarioLogado {

    /** O usuário logado tem a permissão (nome do enum {@code Permissao}, como vem de {@code role_permissao})? */
    public boolean temPermissao(String permissao) {
        return atual()
                .map(usuario -> usuario.getAuthorities().stream()
                        .anyMatch(autoridade -> autoridade.getAuthority().equals(permissao)))
                .orElse(false);
    }

    public Optional<Usuario> atual() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof Usuario usuario) {
            return Optional.of(usuario);
        }
        return Optional.empty();
    }
}
