package com.registraai.registro_coristas_api.telefone.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Renderizada como Problem Details (RFC 7807) com status 409. */
public class TelefoneDuplicadoException extends ErrorResponseException {

    public TelefoneDuplicadoException(String numero) {
        super(HttpStatus.CONFLICT);
        setDetail("Esta pessoa já possui o telefone " + numero);
    }
}
