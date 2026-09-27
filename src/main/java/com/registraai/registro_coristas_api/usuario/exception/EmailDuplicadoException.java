package com.registraai.registro_coristas_api.usuario.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Renderizada como Problem Details (RFC 7807) com status 409. */
public class EmailDuplicadoException extends ErrorResponseException {

    public EmailDuplicadoException(String email) {
        super(HttpStatus.CONFLICT);
        setDetail("Já existe um usuário com o e-mail " + email);
    }
}
