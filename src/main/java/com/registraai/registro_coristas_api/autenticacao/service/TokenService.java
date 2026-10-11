package com.registraai.registro_coristas_api.autenticacao.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Gera e valida o JWT. O token carrega só o e-mail (subject): papéis e status são lidos do banco a cada requisição
 * pelo filtro, então mudança de papel, afastamento ou inativação vale na hora, sem esperar o token expirar.
 */
@Service
public class TokenService {

    private final SecretKey chave;
    private final Duration expiracao;

    public TokenService(@Value("${jwt.segredo}") String segredoBase64,
                        @Value("${jwt.expiracao-minutos}") long expiracaoMinutos) {
        this.chave = Keys.hmacShaKeyFor(Decoders.BASE64.decode(segredoBase64));
        this.expiracao = Duration.ofMinutes(expiracaoMinutos);
    }

    public String gerar(String email) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(email)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(expiracao)))
                .signWith(chave)
                .compact();
    }

    /** Quem é e quando o token foi emitido, ou vazio se estiver malformado, adulterado ou expirado. */
    public Optional<TokenValido> validar(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(chave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (claims.getSubject() == null || claims.getIssuedAt() == null) {
                return Optional.empty();
            }
            return Optional.of(new TokenValido(claims.getSubject(), claims.getIssuedAt().toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
