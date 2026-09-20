package com.registraai.registro_coristas_api.congregacao.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Renderizada como Problem Details (RFC 7807) com status 409. */
public class CongregacaoNomeDuplicadoException extends ErrorResponseException {

    public CongregacaoNomeDuplicadoException(String nome, Integer numeroArea) {
        super(HttpStatus.CONFLICT);
        setDetail("Já existe uma congregação chamada \"" + nome + "\" na área " + numeroArea);
    }
}
