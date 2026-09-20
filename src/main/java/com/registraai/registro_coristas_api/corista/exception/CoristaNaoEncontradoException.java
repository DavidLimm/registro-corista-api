package com.registraai.registro_coristas_api.corista.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.util.UUID;

/** Renderizada como Problem Details (RFC 7807) com status 404. */
public class CoristaNaoEncontradoException extends ErrorResponseException {

    public CoristaNaoEncontradoException(UUID id) {
        super(HttpStatus.NOT_FOUND);
        setDetail("Corista não encontrado: " + id);
    }
}
