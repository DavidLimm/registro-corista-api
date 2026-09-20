package com.registraai.registro_coristas_api.telefone.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.util.UUID;

/** Renderizada como Problem Details (RFC 7807) com status 404. */
public class TelefoneNaoEncontradoException extends ErrorResponseException {

    public TelefoneNaoEncontradoException(UUID id) {
        super(HttpStatus.NOT_FOUND);
        setDetail("Telefone não encontrado: " + id);
    }
}
