package com.registraai.registro_coristas_api.usuario.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Escopo (agrupamentos) incompatível com o papel do usuário. Problem Details com status 409. */
public class EscopoInvalidoException extends ErrorResponseException {

    public EscopoInvalidoException(String detalhe) {
        super(HttpStatus.CONFLICT);
        setDetail(detalhe);
    }
}
