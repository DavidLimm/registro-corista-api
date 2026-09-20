package com.registraai.registro_coristas_api.area.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.util.UUID;

/** Renderizada como Problem Details (RFC 7807) com status 404. */
public class AreaNaoEncontradaException extends ErrorResponseException {

    public AreaNaoEncontradaException(UUID id) {
        super(HttpStatus.NOT_FOUND);
        setDetail("Área não encontrada: " + id);
    }
}
