package com.registraai.registro_coristas_api.agrupamento.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** A subdivisão (ou o agrupamento dela) está inativa. Problem Details com status 409. */
public class SubdivisaoInativaException extends ErrorResponseException {

    public SubdivisaoInativaException() {
        super(HttpStatus.CONFLICT);
        setDetail("A subdivisão (ou o agrupamento a que pertence) está inativa. Reative-a ou escolha outra.");
    }
}
