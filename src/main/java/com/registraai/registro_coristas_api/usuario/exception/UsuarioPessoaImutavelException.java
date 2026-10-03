package com.registraai.registro_coristas_api.usuario.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** A pessoa vinculada a um usuário não muda depois de criada. Renderizada como Problem Details com status 409. */
public class UsuarioPessoaImutavelException extends ErrorResponseException {

    public UsuarioPessoaImutavelException() {
        super(HttpStatus.CONFLICT);
        setDetail("Não é possível trocar a pessoa vinculada a um usuário existente.");
    }
}
