package com.registraai.registro_coristas_api.usuario.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Renderizada como Problem Details (RFC 7807) com status 409. */
public class PapelCoristaExclusivoException extends ErrorResponseException {

    public PapelCoristaExclusivoException() {
        super(HttpStatus.CONFLICT);
        setDetail("Papel de corista não pode ser combinado com outro papel: quem vira líder deixa de ser corista.");
    }
}
