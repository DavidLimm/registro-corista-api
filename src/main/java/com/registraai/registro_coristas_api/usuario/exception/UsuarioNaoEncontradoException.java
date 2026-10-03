package com.registraai.registro_coristas_api.usuario.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.util.UUID;

/** Renderizada como Problem Details (RFC 7807) com status 404. */
public class UsuarioNaoEncontradoException extends ErrorResponseException {

    public UsuarioNaoEncontradoException(UUID id) {
        super(HttpStatus.NOT_FOUND);
        setDetail("Usuário não encontrado: " + id);
    }
}
