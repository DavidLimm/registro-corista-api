package com.registraai.registro_coristas_api.suporte;

import com.registraai.registro_coristas_api.usuario.model.Usuario;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/** {@code Usuario} com autoridades fixas, sem passar pelo banco ({@code role_permissao}). Só para testes. */
public class UsuarioDeTeste extends Usuario {

    private final Collection<GrantedAuthority> autoridades;

    public UsuarioDeTeste(Collection<GrantedAuthority> autoridades) {
        this.autoridades = autoridades;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return autoridades;
    }
}
