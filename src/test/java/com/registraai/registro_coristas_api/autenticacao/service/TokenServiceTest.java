package com.registraai.registro_coristas_api.autenticacao.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenServiceTest {

    private static final String SEGREDO = "6fxzEGdzPXqamvjE0C37s5N/0moHscFg+i9cNn4RZJY=";
    private static final String OUTRO_SEGREDO = "yyvv6Ttu9ES2eHoqsRX6Mo55FTgEJfyQk1Q31m25eUQ=";

    private final TokenService tokenService = new TokenService(SEGREDO, 60);

    @Test
    void gerarEValidar_devolveOEmailDoToken() {
        String token = tokenService.gerar("ana@exemplo.com");

        assertThat(tokenService.validar(token)).map(TokenValido::email).contains("ana@exemplo.com");
        assertThat(tokenService.validar(token).orElseThrow().emitidoEm()).isBeforeOrEqualTo(java.time.Instant.now());
    }

    @Test
    void validar_tokenComAssinaturaAdulterada_devolveVazio() {
        String[] partes = tokenService.gerar("ana@exemplo.com").split("\\.");
        char primeira = partes[2].charAt(0);
        String assinaturaTrocada = (primeira == 'A' ? 'B' : 'A') + partes[2].substring(1);

        assertThat(tokenService.validar(partes[0] + "." + partes[1] + "." + assinaturaTrocada)).isEmpty();
    }

    @Test
    void validar_tokenAssinadoComOutraChave_devolveVazio() {
        String token = new TokenService(OUTRO_SEGREDO, 60).gerar("ana@exemplo.com");

        assertThat(tokenService.validar(token)).isEmpty();
    }

    @Test
    void validar_tokenExpirado_devolveVazio() {
        String token = new TokenService(SEGREDO, -1).gerar("ana@exemplo.com");

        assertThat(tokenService.validar(token)).isEmpty();
    }

    @Test
    void validar_textoQueNaoEUmToken_devolveVazio() {
        assertThat(tokenService.validar("isto-nao-e-um-jwt")).isEmpty();
        assertThat(tokenService.validar("")).isEmpty();
        assertThat(tokenService.validar("a.b.c")).isEmpty();
    }
}
