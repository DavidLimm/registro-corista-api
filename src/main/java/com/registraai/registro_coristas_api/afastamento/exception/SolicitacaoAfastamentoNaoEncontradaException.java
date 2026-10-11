package com.registraai.registro_coristas_api.afastamento.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.util.UUID;

/** Renderizada como Problem Details (RFC 7807) com status 404. */
public class SolicitacaoAfastamentoNaoEncontradaException extends ErrorResponseException {

    public SolicitacaoAfastamentoNaoEncontradaException(UUID id) {
        super(HttpStatus.NOT_FOUND);
        setDetail("Solicitação de afastamento não encontrada: " + id);
    }
}
