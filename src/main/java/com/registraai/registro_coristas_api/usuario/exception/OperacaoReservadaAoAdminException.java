package com.registraai.registro_coristas_api.usuario.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Renderizada como Problem Details (RFC 7807) com status 403. */
public class OperacaoReservadaAoAdminException extends ErrorResponseException {

    public OperacaoReservadaAoAdminException(String detalhe) {
        super(HttpStatus.FORBIDDEN);
        setDetail(detalhe);
    }
}
