package com.registraai.registro_coristas_api.role.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Renderizada como Problem Details (RFC 7807) com status 409. */
public class RoleProtegidaException extends ErrorResponseException {

    public RoleProtegidaException(String nome) {
        super(HttpStatus.CONFLICT);
        setDetail("As permissões do papel " + nome + " não podem ser alteradas: ele sempre tem todas.");
    }
}
