package com.registraai.registro_coristas_api.usuario.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.util.UUID;

/** Uma pessoa tem no máximo um AppUser (relação 1:1). Renderizada como Problem Details com status 409. */
public class PessoaJaPossuiUsuarioException extends ErrorResponseException {

    public PessoaJaPossuiUsuarioException(UUID pessoaId) {
        super(HttpStatus.CONFLICT);
        setDetail("A pessoa " + pessoaId + " já possui um usuário cadastrado.");
    }
}
