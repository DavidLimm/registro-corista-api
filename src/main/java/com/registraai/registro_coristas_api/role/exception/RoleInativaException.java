package com.registraai.registro_coristas_api.role.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Renderizada como Problem Details (RFC 7807) com status 409. */
public class RoleInativaException extends ErrorResponseException {

    public RoleInativaException(String nome) {
        super(HttpStatus.CONFLICT);
        setDetail("O papel (role) \"" + nome + "\" está inativo. Escolha um papel ativo.");
    }
}
