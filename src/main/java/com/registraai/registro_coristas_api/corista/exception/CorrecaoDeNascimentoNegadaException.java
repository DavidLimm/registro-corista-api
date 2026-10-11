package com.registraai.registro_coristas_api.corista.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Renderizada como Problem Details (RFC 7807) com status 403. */
public class CorrecaoDeNascimentoNegadaException extends ErrorResponseException {

    public CorrecaoDeNascimentoNegadaException() {
        super(HttpStatus.FORBIDDEN);
        setDetail("Só um líder pode corrigir a data de nascimento. Procure o líder da sua mocidade.");
    }
}
