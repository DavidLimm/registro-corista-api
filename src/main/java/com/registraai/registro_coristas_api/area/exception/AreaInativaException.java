package com.registraai.registro_coristas_api.area.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Renderizada como Problem Details (RFC 7807) com status 409. */
public class AreaInativaException extends ErrorResponseException {

    public AreaInativaException(Integer numeroArea) {
        super(HttpStatus.CONFLICT);
        setDetail("A área " + numeroArea + " está inativa. Reative-a ou escolha outra área ativa.");
    }
}
