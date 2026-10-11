package com.registraai.registro_coristas_api.autenticacao.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Conta bloqueada por tentativas de senha. Problem Details com 423 (Locked). */
public class ContaBloqueadaException extends ErrorResponseException {

    public ContaBloqueadaException() {
        super(HttpStatus.LOCKED);
        setDetail("Conta bloqueada por tentativas de senha incorreta. Redefina sua senha para voltar a acessar.");
    }
}
