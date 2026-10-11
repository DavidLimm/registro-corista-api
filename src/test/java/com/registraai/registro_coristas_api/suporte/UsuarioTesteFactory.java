package com.registraai.registro_coristas_api.suporte;

import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.pessoa.model.StatusPessoa;
import com.registraai.registro_coristas_api.role.model.Permissao;
import com.registraai.registro_coristas_api.role.model.Role;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UsuarioTesteFactory implements WithSecurityContextFactory<WithUsuarioTeste> {

    @Override
    public SecurityContext createSecurityContext(WithUsuarioTeste anotacao) {
        return contexto(anotacao.papeis(), anotacao.todasPermissoes(), anotacao.permissoes());
    }

    /** Também serve a testes que escolhem o usuário por requisição ({@code securityContext(...)}), sem anotação. */
    public static SecurityContext contexto(String[] papeisDoUsuario, boolean todasPermissoes, String[] permissoesDoUsuario) {
        Set<Role> roles = Arrays.stream(papeisDoUsuario)
                .map(nome -> Role.builder().id(UUID.randomUUID()).nome(nome).build())
                .collect(Collectors.toSet());

        Set<GrantedAuthority> autoridades = new HashSet<>();
        Arrays.stream(papeisDoUsuario).forEach(nome -> autoridades.add(new SimpleGrantedAuthority("ROLE_" + nome)));
        Stream<String> permissoes = todasPermissoes
                ? Arrays.stream(Permissao.values()).map(Enum::name)
                : Arrays.stream(permissoesDoUsuario);
        permissoes.forEach(nome -> autoridades.add(new SimpleGrantedAuthority(nome)));

        UsuarioDeTeste usuario = new UsuarioDeTeste(autoridades);
        usuario.setId(UUID.fromString(WithUsuarioTeste.ID_PADRAO));
        usuario.setEmail("teste@teste.com");
        usuario.setSenhaHash("hash");
        usuario.setAtivo(true);
        usuario.setRoles(roles);
        usuario.setPessoa(Pessoa.builder().id(UUID.randomUUID()).nome("Usuário de teste")
                .status(StatusPessoa.APROVADO).build());

        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(usuario, null, autoridades));
        return contexto;
    }
}
