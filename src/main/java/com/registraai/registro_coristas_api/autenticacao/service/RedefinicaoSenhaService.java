package com.registraai.registro_coristas_api.autenticacao.service;

import com.registraai.registro_coristas_api.autenticacao.dto.ConfirmacaoRedefinicaoRequest;
import com.registraai.registro_coristas_api.autenticacao.email.EnviadorDeEmail;
import com.registraai.registro_coristas_api.autenticacao.exception.TokenDeRedefinicaoInvalidoException;
import com.registraai.registro_coristas_api.autenticacao.model.RedefinicaoSenha;
import com.registraai.registro_coristas_api.autenticacao.repository.RedefinicaoSenhaRepository;
import com.registraai.registro_coristas_api.usuario.model.Usuario;
import com.registraai.registro_coristas_api.usuario.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Redefinição de senha por token enviado por e-mail. O token é aleatório (256 bits), vale por pouco tempo, só pode
 * ser usado uma vez, e só o seu hash SHA-256 fica no banco. Redefinir também libera a conta bloqueada por tentativas.
 */
@Service
public class RedefinicaoSenhaService {

    // pedidos seguidos do mesmo usuário em menos disso são ignorados (evita encher a caixa de e-mail de alguém)
    private static final Duration INTERVALO_MINIMO_ENTRE_PEDIDOS = Duration.ofMinutes(1);

    private final UsuarioRepository usuarioRepository;
    private final RedefinicaoSenhaRepository redefinicaoSenhaRepository;
    private final EnviadorDeEmail enviadorDeEmail;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Duration validade;
    private final SecureRandom aleatorio = new SecureRandom();

    public RedefinicaoSenhaService(UsuarioRepository usuarioRepository,
                                   RedefinicaoSenhaRepository redefinicaoSenhaRepository,
                                   EnviadorDeEmail enviadorDeEmail,
                                   PasswordEncoder passwordEncoder,
                                   Clock clock,
                                   @Value("${seguranca.redefinicao-senha.validade-minutos:30}") long validadeEmMinutos) {
        this.usuarioRepository = usuarioRepository;
        this.redefinicaoSenhaRepository = redefinicaoSenhaRepository;
        this.enviadorDeEmail = enviadorDeEmail;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.validade = Duration.ofMinutes(validadeEmMinutos);
    }

    /**
     * Sempre termina igual, exista o e-mail ou não (quem chama responde 202 de qualquer jeito), para não revelar quais
     * e-mails têm conta.
     */
    @Transactional
    public void solicitar(String emailInformado) {
        String email = emailInformado.trim().toLowerCase();
        usuarioRepository.findByEmail(email).filter(Usuario::isAtivo).ifPresent(usuario -> {
            Instant agora = Instant.now(clock);
            if (redefinicaoSenhaRepository.existsByUsuarioIdAndCriadoEmAfter(
                    usuario.getId(), agora.minus(INTERVALO_MINIMO_ENTRE_PEDIDOS))) {
                return;
            }
            redefinicaoSenhaRepository.invalidarPendentes(usuario.getId(), agora);
            String token = novoToken();
            redefinicaoSenhaRepository.save(RedefinicaoSenha.builder()
                    .usuario(usuario)
                    .tokenHash(hash(token))
                    .expiraEm(agora.plus(validade))
                    .build());
            enviadorDeEmail.enviarRedefinicaoDeSenha(usuario.getEmail(), token);
        });
    }

    @Transactional
    public void confirmar(ConfirmacaoRedefinicaoRequest request) {
        Instant agora = Instant.now(clock);
        RedefinicaoSenha pedido = redefinicaoSenhaRepository.findByTokenHash(hash(request.token().trim()))
                .filter(r -> r.getUsadoEm() == null && r.getExpiraEm().isAfter(agora))
                .orElseThrow(TokenDeRedefinicaoInvalidoException::new);

        Usuario usuario = pedido.getUsuario();
        usuario.setSenhaHash(passwordEncoder.encode(request.novaSenha()));
        usuario.setSenhaAlteradaEm(agora);
        usuario.setTentativasFalhas(0);
        usuario.setBloqueadoEm(null);
        usuarioRepository.save(usuario);

        pedido.setUsadoEm(agora);
        redefinicaoSenhaRepository.save(pedido);
        // qualquer outro link ainda pendente deste usuário também morre
        redefinicaoSenhaRepository.invalidarPendentes(usuario.getId(), agora);
    }

    private String novoToken() {
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumo);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
