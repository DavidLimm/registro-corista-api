package com.registraai.registro_coristas_api.pessoa.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** LGPD: menor de 18 anos sem consentimento explícito. Status 400. */
public class ConsentimentoLgpdObrigatorioException extends ErrorResponseException {

    public ConsentimentoLgpdObrigatorioException() {
        super(HttpStatus.BAD_REQUEST);
        setDetail("Menores de 18 anos exigem consentimento explícito (LGPD).");
    }
}
