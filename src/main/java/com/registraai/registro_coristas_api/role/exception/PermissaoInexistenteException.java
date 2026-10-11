package com.registraai.registro_coristas_api.role.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.util.Collection;

/** Renderizada como Problem Details (RFC 7807) com status 400. */
public class PermissaoInexistenteException extends ErrorResponseException {

    public PermissaoInexistenteException(Collection<String> nomes) {
        super(HttpStatus.BAD_REQUEST);
        setDetail("Permissão inexistente: " + String.join(", ", nomes));
    }
}
