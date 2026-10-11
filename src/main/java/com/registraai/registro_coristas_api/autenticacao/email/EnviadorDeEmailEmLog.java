package com.registraai.registro_coristas_api.autenticacao.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Só fora de produção: o token aparece no log para dar para testar o fluxo sem servidor de e-mail. */
@Slf4j
@Component
@Profile("!prod")
public class EnviadorDeEmailEmLog implements EnviadorDeEmail {

    @Override
    public void enviarRedefinicaoDeSenha(String destinatario, String token) {
        log.warn("[DEV] Redefinição de senha para {}: token = {}", destinatario, token);
    }
}
