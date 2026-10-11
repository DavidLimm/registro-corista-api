package com.registraai.registro_coristas_api.autenticacao.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Problem Details com 400: a nova senha repete a atual. */
public class NovaSenhaIgualException extends ErrorResponseException {

    public NovaSenhaIgualException() {
        super(HttpStatus.BAD_REQUEST);
        setDetail("A nova senha deve ser diferente da atual.");
    }
}
