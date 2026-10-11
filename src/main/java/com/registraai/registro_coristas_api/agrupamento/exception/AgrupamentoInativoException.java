package com.registraai.registro_coristas_api.agrupamento.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Renderizada como Problem Details (RFC 7807) com status 409. */
public class AgrupamentoInativoException extends ErrorResponseException {

    public AgrupamentoInativoException() {
        super(HttpStatus.CONFLICT);
        setDetail("O agrupamento está inativo; reative-o antes de criar subdivisões nele");
    }
}
