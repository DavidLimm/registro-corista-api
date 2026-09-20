package com.registraai.registro_coristas_api.pessoa.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Responsável legal com só um dos campos (a CHECK do banco exige nome e telefone juntos). Status 400. */
public class ResponsavelLegalIncompletoException extends ErrorResponseException {

    public ResponsavelLegalIncompletoException() {
        super(HttpStatus.BAD_REQUEST);
        setDetail("Informe nome e telefone do responsável legal, ou nenhum dos dois.");
    }
}
