package com.registraai.registro_coristas_api.autenticacao.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Token inexistente, já usado ou expirado: a mesma resposta para os três, para não ensinar nada a quem tenta adivinhar. */
public class TokenDeRedefinicaoInvalidoException extends ErrorResponseException {

    public TokenDeRedefinicaoInvalidoException() {
        super(HttpStatus.BAD_REQUEST);
        setDetail("Link de redefinição inválido ou expirado. Solicite um novo.");
    }
}
