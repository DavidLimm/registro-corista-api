package com.registraai.registro_coristas_api.role.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.util.UUID;

/** Renderizada como Problem Details (RFC 7807) com status 404. */
public class RoleNaoEncontradaException extends ErrorResponseException {

    public RoleNaoEncontradaException(UUID id) {
        super(HttpStatus.NOT_FOUND);
        setDetail("Papel (role) não encontrado: " + id);
    }
}
