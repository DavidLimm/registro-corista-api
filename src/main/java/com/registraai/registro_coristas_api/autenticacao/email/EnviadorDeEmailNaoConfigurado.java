package com.registraai.registro_coristas_api.autenticacao.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Produção sem provedor de e-mail: não envia nada e não registra o token (log não é lugar de segredo). Enquanto este
 * for o único enviador, a redefinição de senha por conta própria não funciona; é preciso implementar o envio real.
 */
@Slf4j
@Component
@Profile("prod")
public class EnviadorDeEmailNaoConfigurado implements EnviadorDeEmail {

    @Override
    public void enviarRedefinicaoDeSenha(String destinatario, String token) {
        log.error("Redefinição de senha pedida, mas não há envio de e-mail configurado (destinatário {}).", destinatario);
    }
}
