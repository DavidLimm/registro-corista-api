package com.registraai.registro_coristas_api.autenticacao.service;

import com.registraai.registro_coristas_api.autenticacao.dto.LoginRequest;
import com.registraai.registro_coristas_api.autenticacao.dto.LoginResponse;
import com.registraai.registro_coristas_api.autenticacao.exception.CadastroSemAcessoException;
import com.registraai.registro_coristas_api.autenticacao.exception.ContaBloqueadaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutenticacaoServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private TokenService tokenService;

    @Mock
    private ControleDeLogin controleDeLogin;

    private AutenticacaoService service() {
        return new AutenticacaoService(authenticationManager, tokenService, controleDeLogin);
    }

    @Test
    void login_sucesso_devolveTokenEmailEAutoridadesEZeraAsFalhas() {
        Authentication autenticada = UsernamePasswordAuthenticationToken.authenticated("ana@exemplo.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_CORISTA_JOVENS"), new SimpleGrantedAuthority("CORISTA_ATUALIZAR")));
        when(authenticationManager.authenticate(any())).thenReturn(autenticada);
        when(tokenService.gerar("ana@exemplo.com")).thenReturn("token-jwt");

        LoginResponse resposta = service().login(new LoginRequest("ana@exemplo.com", "SenhaForte@123"));

        assertThat(resposta.token()).isEqualTo("token-jwt");
        assertThat(resposta.email()).isEqualTo("ana@exemplo.com");
        assertThat(resposta.roles()).containsExactlyInAnyOrder("ROLE_CORISTA_JOVENS", "CORISTA_ATUALIZAR");
        verify(controleDeLogin).registrarSucesso("ana@exemplo.com");
    }

    @Test
    void login_normalizaOEmailAntesDeAutenticar() {
        Authentication autenticada = UsernamePasswordAuthenticationToken.authenticated("ana@exemplo.com", null, List.of());
        when(authenticationManager.authenticate(any())).thenReturn(autenticada);

        service().login(new LoginRequest("  Ana@Exemplo.COM ", "SenhaForte@123"));

        ArgumentCaptor<Authentication> captor = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("ana@exemplo.com");
    }

    @Test
    void login_contaBloqueada_negaMesmoComSenhaCertaSemTentarAutenticar() {
        when(controleDeLogin.estaBloqueada("ana@exemplo.com")).thenReturn(true);

        assertThatThrownBy(() -> service().login(new LoginRequest("ana@exemplo.com", "SenhaForte@123")))
                .isInstanceOf(ContaBloqueadaException.class);
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void login_senhaErrada_contaAFalhaEPropagaBadCredentialsSeAindaNaoBloqueou() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("ruim"));
        when(controleDeLogin.registrarFalha("ana@exemplo.com")).thenReturn(false);

        assertThatThrownBy(() -> service().login(new LoginRequest("ana@exemplo.com", "errada")))
                .isInstanceOf(BadCredentialsException.class);
        verify(controleDeLogin, never()).registrarSucesso(any());
    }

    @Test
    void login_senhaErradaQueAtingeOLimite_respondeContaBloqueada() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("ruim"));
        when(controleDeLogin.registrarFalha("ana@exemplo.com")).thenReturn(true);

        assertThatThrownBy(() -> service().login(new LoginRequest("ana@exemplo.com", "errada")))
                .isInstanceOf(ContaBloqueadaException.class);
    }

    @Test
    void login_cadastroNaoLiberado_viraCadastroSemAcessoComOMotivoESemContarFalha() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new DisabledException("Seu cadastro está pendente de aprovação."));

        assertThatThrownBy(() -> service().login(new LoginRequest("ana@exemplo.com", "SenhaForte@123")))
                .isInstanceOf(CadastroSemAcessoException.class)
                .satisfies(e -> assertThat(((CadastroSemAcessoException) e).getBody().getDetail())
                        .isEqualTo("Seu cadastro está pendente de aprovação."));
        verify(controleDeLogin, never()).registrarFalha(any());
    }
}
