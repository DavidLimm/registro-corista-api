package com.registraai.registro_coristas_api.autenticacao.filter;

import com.registraai.registro_coristas_api.autenticacao.service.TokenService;
import com.registraai.registro_coristas_api.autenticacao.service.TokenValido;
import com.registraai.registro_coristas_api.usuario.model.Usuario;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.temporal.ChronoUnit;

/**
 * Lê o {@code Authorization: Bearer <token>}, valida o JWT e popula o {@code SecurityContext}. Não rejeita nada
 * sozinho: sem token (ou token inválido) a requisição segue anônima e quem barra é o {@code SecurityFilterChain}
 * (401). O usuário é recarregado do banco a cada requisição e só autentica se {@code isEnabled()} — assim afastar,
 * reprovar ou inativar alguém bloqueia na hora, mesmo com token ainda dentro da validade.
 *
 * <p>Não é {@code @Component} de propósito: o Spring Boot registraria o filtro também no container servlet,
 * fora da cadeia do Security. Ele é instanciado em {@code SegurancaConfig}.
 */
@RequiredArgsConstructor
public class JwtAutenticacaoFilter extends OncePerRequestFilter {

    private static final String PREFIXO_BEARER = "Bearer ";

    private final TokenService tokenService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String cabecalho = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (cabecalho != null && cabecalho.startsWith(PREFIXO_BEARER)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            tokenService.validar(cabecalho.substring(PREFIXO_BEARER.length()))
                    .ifPresent(token -> autenticar(token, request));
        }

        filterChain.doFilter(request, response);
    }

    private void autenticar(TokenValido token, HttpServletRequest request) {
        try {
            UserDetails usuario = userDetailsService.loadUserByUsername(token.email());
            if (!usuario.isEnabled() || !usuario.isAccountNonLocked() || emitidoAntesDaTrocaDeSenha(usuario, token)) {
                return;
            }
            UsernamePasswordAuthenticationToken autenticacao =
                    UsernamePasswordAuthenticationToken.authenticated(usuario, null, usuario.getAuthorities());
            autenticacao.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(autenticacao);
        } catch (UsernameNotFoundException e) {
            // token válido de usuário que não existe mais: segue anônimo
        }
    }

    // o "iat" do JWT tem precisão de segundos, então a comparação também é em segundos
    private boolean emitidoAntesDaTrocaDeSenha(UserDetails usuario, TokenValido token) {
        return usuario instanceof Usuario u
                && u.getSenhaAlteradaEm() != null
                && token.emitidoEm().isBefore(u.getSenhaAlteradaEm().truncatedTo(ChronoUnit.SECONDS));
    }
}
