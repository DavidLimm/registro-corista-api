package com.registraai.registro_coristas_api.autenticacao.service;

import com.registraai.registro_coristas_api.autenticacao.dto.LoginRequest;
import com.registraai.registro_coristas_api.autenticacao.dto.LoginResponse;
import com.registraai.registro_coristas_api.autenticacao.exception.CadastroSemAcessoException;
import com.registraai.registro_coristas_api.autenticacao.exception.ContaBloqueadaException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AutenticacaoService {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final ControleDeLogin controleDeLogin;

    /**
     * Autentica por e-mail/senha.
     * <ul>
     *   <li>Conta bloqueada por tentativas: {@link ContaBloqueadaException} (423), mesmo com a senha certa, até redefinir.</li>
     *   <li>Senha errada ou e-mail inexistente: {@code BadCredentialsException} (401); cada senha errada conta, e a
     *       terceira seguida bloqueia (e já responde 423).</li>
     *   <li>Senha certa com cadastro não liberado (pendente, reprovado, afastado, inativo): {@link CadastroSemAcessoException}
     *       (403) com o motivo; isso não conta como tentativa errada.</li>
     * </ul>
     */
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        if (controleDeLogin.estaBloqueada(email)) {
            throw new ContaBloqueadaException();
        }

        Authentication autenticacao;
        try {
            autenticacao = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.senha()));
        } catch (BadCredentialsException e) {
            if (controleDeLogin.registrarFalha(email)) {
                throw new ContaBloqueadaException();
            }
            throw e;
        } catch (DisabledException e) {
            // só chega aqui com a senha correta (VerificadorDeAcesso); a mensagem diz por que o acesso não está liberado
            throw new CadastroSemAcessoException(e.getMessage());
        }

        controleDeLogin.registrarSucesso(email);
        return new LoginResponse(
                tokenService.gerar(autenticacao.getName()),
                autenticacao.getName(),
                autenticacao.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }
}
