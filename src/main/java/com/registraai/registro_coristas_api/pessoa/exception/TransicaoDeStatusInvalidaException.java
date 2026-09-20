package com.registraai.registro_coristas_api.pessoa.exception;

import com.registraai.registro_coristas_api.pessoa.model.StatusPessoa;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Renderizada como Problem Details (RFC 7807) com status 409. */
public class TransicaoDeStatusInvalidaException extends ErrorResponseException {

    public TransicaoDeStatusInvalidaException(StatusPessoa atual, StatusPessoa desejado) {
        super(HttpStatus.CONFLICT);
        setDetail("Não é possível passar o cadastro de " + atual + " para " + desejado + ".");
    }
}
