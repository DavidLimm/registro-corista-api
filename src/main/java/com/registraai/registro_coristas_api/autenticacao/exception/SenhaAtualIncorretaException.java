package com.registraai.registro_coristas_api.autenticacao.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Problem Details com 400 (não 401, que o cliente leria como "sessão expirada"). */
public class SenhaAtualIncorretaException extends ErrorResponseException {

    public SenhaAtualIncorretaException() {
        super(HttpStatus.BAD_REQUEST);
        setDetail("A senha atual está incorreta.");
    }
}
