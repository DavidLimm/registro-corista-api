package com.registraai.registro_coristas_api.autenticacao.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Ação sobre um recurso fora dos agrupamentos do usuário, quando o recurso não é secreto (ex.: congregações são
 * públicas). Para dados de pessoas a resposta é 404, para não revelar que existem. Problem Details com status 403.
 */
public class ForaDoEscopoException extends ErrorResponseException {

    public ForaDoEscopoException() {
        super(HttpStatus.FORBIDDEN);
        setDetail("Esta ação está fora dos agrupamentos a que você tem acesso.");
    }
}
