package com.registraai.registro_coristas_api.usuario.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.util.UUID;

/** Só pessoa com cadastro APROVADO pode ganhar login. Renderizada como Problem Details com status 409. */
public class PessoaNaoAprovadaException extends ErrorResponseException {

    public PessoaNaoAprovadaException(UUID pessoaId) {
        super(HttpStatus.CONFLICT);
        setDetail("A pessoa " + pessoaId + " ainda não está com o cadastro APROVADO.");
    }
}
