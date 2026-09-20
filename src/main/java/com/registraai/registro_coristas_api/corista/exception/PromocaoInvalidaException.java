package com.registraai.registro_coristas_api.corista.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Renderizada como Problem Details (RFC 7807) com status 409. */
public class PromocaoInvalidaException extends ErrorResponseException {

    public PromocaoInvalidaException(String motivo) {
        super(HttpStatus.CONFLICT);
        setDetail("Promoção antecipada inválida: " + motivo);
    }
}
