package com.registraai.registro_coristas_api.pessoa.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** LGPD: menor de 18 anos sem responsável legal. Status 400. */
public class ResponsavelLegalObrigatorioException extends ErrorResponseException {

    public ResponsavelLegalObrigatorioException() {
        super(HttpStatus.BAD_REQUEST);
        setDetail("Menores de 18 anos exigem responsável legal (nome e telefone).");
    }
}
