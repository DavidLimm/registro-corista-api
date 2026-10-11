package com.registraai.registro_coristas_api.usuario.service;

import com.registraai.registro_coristas_api.role.model.Role;
import com.registraai.registro_coristas_api.usuario.model.Usuario;

import java.util.Collection;

/** Papéis com tratamento especial no escopo e na concessão de papéis. */
public final class Papeis {

    public static final String ADMIN = "ADMIN";
    public static final String PASTOR_ADMIN = "PASTOR_ADMIN";
    public static final String PASTOR = "PASTOR";

    private Papeis() {
    }

    /** ADMIN e PASTOR_ADMIN enxergam tudo: não têm vínculo com agrupamento. */
    public static boolean ehGlobal(Collection<Role> roles) {
        return tem(roles, ADMIN) || tem(roles, PASTOR_ADMIN);
    }

    public static boolean ehGlobal(Usuario usuario) {
        return ehGlobal(usuario.getRoles());
    }

    public static boolean tem(Collection<Role> roles, String nome) {
        return roles.stream().anyMatch(role -> nome.equals(role.getNome()));
    }

    public static boolean tem(Usuario usuario, String nome) {
        return tem(usuario.getRoles(), nome);
    }
}
