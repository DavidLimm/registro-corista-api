package com.registraai.registro_coristas_api.autenticacao.service;

import com.registraai.registro_coristas_api.autenticacao.dto.TrocaSenhaRequest;
import com.registraai.registro_coristas_api.autenticacao.exception.NovaSenhaIgualException;
import com.registraai.registro_coristas_api.autenticacao.exception.SenhaAtualIncorretaException;
import com.registraai.registro_coristas_api.usuario.model.Usuario;
import com.registraai.registro_coristas_api.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Troca de senha de quem já está logado. Os tokens emitidos antes da troca deixam de valer. */
@Service
@RequiredArgsConstructor
public class SenhaService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional
    public void trocar(UUID usuarioId, TrocaSenhaRequest request) {
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        if (!passwordEncoder.matches(request.senhaAtual(), usuario.getSenhaHash())) {
            throw new SenhaAtualIncorretaException();
        }
        if (request.senhaAtual().equals(request.novaSenha())) {
            throw new NovaSenhaIgualException();
        }
        usuario.setSenhaHash(passwordEncoder.encode(request.novaSenha()));
        usuario.setSenhaAlteradaEm(Instant.now(clock));
        usuarioRepository.save(usuario);
    }
}
