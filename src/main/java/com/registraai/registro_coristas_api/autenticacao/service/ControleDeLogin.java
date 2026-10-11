package com.registraai.registro_coristas_api.autenticacao.service;

import com.registraai.registro_coristas_api.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Bloqueio por tentativas: {@value #LIMITE_DE_FALHAS} senhas erradas seguidas bloqueiam a conta, e só a redefinição
 * de senha a libera. Cada operação é um UPDATE atômico e roda na própria transação, então a falha de login (que
 * termina em exceção) não desfaz a contagem.
 */
@Service
@RequiredArgsConstructor
public class ControleDeLogin {

    public static final int LIMITE_DE_FALHAS = 3;

    private final UsuarioRepository usuarioRepository;
    private final Clock clock;

    public boolean estaBloqueada(String email) {
        return usuarioRepository.existsByEmailAndBloqueadoEmIsNotNull(email);
    }

    /** Soma uma falha e bloqueia se chegou ao limite. Devolve {@code true} se a conta ficou (ou já estava) bloqueada. */
    @Transactional
    public boolean registrarFalha(String email) {
        usuarioRepository.registrarFalhaDeLogin(email);
        usuarioRepository.bloquearSeAtingiuOLimite(email, LIMITE_DE_FALHAS, Instant.now(clock));
        return usuarioRepository.existsByEmailAndBloqueadoEmIsNotNull(email);
    }

    @Transactional
    public void registrarSucesso(String email) {
        usuarioRepository.zerarFalhasDeLogin(email);
    }
}
