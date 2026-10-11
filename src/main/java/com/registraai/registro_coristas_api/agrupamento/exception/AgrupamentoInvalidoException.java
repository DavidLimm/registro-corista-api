package com.registraai.registro_coristas_api.agrupamento.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Campos incompatíveis com o tipo do agrupamento. Renderizada como Problem Details com status 400. */
public class AgrupamentoInvalidoException extends ErrorResponseException {

    public AgrupamentoInvalidoException(String detalhe) {
        super(HttpStatus.BAD_REQUEST);
        setDetail(detalhe);
    }
}
