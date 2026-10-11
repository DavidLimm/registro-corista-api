package com.registraai.registro_coristas_api.autenticacao.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Senha correta, mas o cadastro não está liberado (pendente, reprovado, afastado...). Problem Details com 403. */
public class CadastroSemAcessoException extends ErrorResponseException {

    public CadastroSemAcessoException(String motivo) {
        super(HttpStatus.FORBIDDEN);
        setDetail(motivo);
    }
}
