package com.registraai.registro_coristas_api.role.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Renderizada como Problem Details (RFC 7807) com status 409. */
public class RoleNomeDuplicadoException extends ErrorResponseException {

    public RoleNomeDuplicadoException(String nome) {
        super(HttpStatus.CONFLICT);
        setDetail("Já existe um papel (role) com o nome " + nome);
    }
}
