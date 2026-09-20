package com.registraai.registro_coristas_api.area.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Renderizada como Problem Details (RFC 7807) com status 409. */
public class AreaNumeroDuplicadoException extends ErrorResponseException {

    public AreaNumeroDuplicadoException(Integer numero) {
        super(HttpStatus.CONFLICT);
        setDetail("Já existe uma área com o número " + numero);
    }
}
