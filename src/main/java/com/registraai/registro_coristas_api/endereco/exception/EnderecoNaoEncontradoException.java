package com.registraai.registro_coristas_api.endereco.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.util.UUID;

/** Renderizada como Problem Details (RFC 7807) com status 404. */
public class EnderecoNaoEncontradoException extends ErrorResponseException {

    public EnderecoNaoEncontradoException(UUID id) {
        super(HttpStatus.NOT_FOUND);
        setDetail("Endereço não encontrado: " + id);
    }
}
