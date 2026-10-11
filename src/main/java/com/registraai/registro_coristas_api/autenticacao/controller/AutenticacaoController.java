package com.registraai.registro_coristas_api.autenticacao.controller;

import com.registraai.registro_coristas_api.autenticacao.dto.ConfirmacaoRedefinicaoRequest;
import com.registraai.registro_coristas_api.autenticacao.dto.LoginRequest;
import com.registraai.registro_coristas_api.autenticacao.dto.LoginResponse;
import com.registraai.registro_coristas_api.autenticacao.dto.SolicitacaoRedefinicaoRequest;
import com.registraai.registro_coristas_api.autenticacao.dto.TrocaSenhaRequest;
import com.registraai.registro_coristas_api.autenticacao.service.AutenticacaoService;
import com.registraai.registro_coristas_api.autenticacao.service.RedefinicaoSenhaService;
import com.registraai.registro_coristas_api.autenticacao.service.SenhaService;
import com.registraai.registro_coristas_api.usuario.model.Usuario;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/api/auth")
@RequiredArgsConstructor
public class AutenticacaoController {

    private final AutenticacaoService autenticacaoService;
    private final SenhaService senhaService;
    private final RedefinicaoSenhaService redefinicaoSenhaService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return autenticacaoService.login(request);
    }

    /** Troca a senha de quem está logado. Os tokens antigos deixam de valer: é preciso entrar de novo. */
    @PutMapping("/senha")
    public ResponseEntity<Void> trocarSenha(@AuthenticationPrincipal Usuario usuario,
                                            @Valid @RequestBody TrocaSenhaRequest request) {
        senhaService.trocar(usuario.getId(), request);
        return ResponseEntity.noContent().build();
    }

    /** Público. Responde 202 exista o e-mail ou não, para não revelar quais e-mails têm conta. */
    @PostMapping("/redefinicao-senha/solicitar")
    public ResponseEntity<Void> solicitarRedefinicao(@Valid @RequestBody SolicitacaoRedefinicaoRequest request) {
        redefinicaoSenhaService.solicitar(request.email());
        return ResponseEntity.accepted().build();
    }

    /** Público (quem esqueceu a senha não tem login). Também libera a conta bloqueada por tentativas. */
    @PostMapping("/redefinicao-senha/confirmar")
    public ResponseEntity<Void> confirmarRedefinicao(@Valid @RequestBody ConfirmacaoRedefinicaoRequest request) {
        redefinicaoSenhaService.confirmar(request);
        return ResponseEntity.noContent().build();
    }
}
