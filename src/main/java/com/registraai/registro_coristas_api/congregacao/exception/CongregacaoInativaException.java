package com.registraai.registro_coristas_api.congregacao.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Renderizada como Problem Details (RFC 7807) com status 409. */
public class CongregacaoInativaException extends ErrorResponseException {

    public CongregacaoInativaException(String nome) {
        super(HttpStatus.CONFLICT);
        setDetail("A congregação \"" + nome + "\" está inativa. Escolha uma congregação ativa.");
    }
}
