package com.registraai.registro_coristas_api.agrupamento.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.util.UUID;

/** Renderizada como Problem Details (RFC 7807) com status 404. */
public class AgrupamentoNaoEncontradoException extends ErrorResponseException {

    public AgrupamentoNaoEncontradoException(UUID id) {
        super(HttpStatus.NOT_FOUND);
        setDetail("Agrupamento não encontrado: " + id);
    }
}
