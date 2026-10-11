package com.registraai.registro_coristas_api.agrupamento.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Número ou nome já usado no mesmo escopo. Renderizada como Problem Details com status 409. */
public class AgrupamentoDuplicadoException extends ErrorResponseException {

    public AgrupamentoDuplicadoException(String detalhe) {
        super(HttpStatus.CONFLICT);
        setDetail(detalhe);
    }
}
