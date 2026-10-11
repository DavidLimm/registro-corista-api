package com.registraai.registro_coristas_api.afastamento.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Pedido que não pode ser feito ou decidido agora (pessoa não aprovada, já há pedido pendente, já decidido). 409. */
public class SolicitacaoAfastamentoInvalidaException extends ErrorResponseException {

    public SolicitacaoAfastamentoInvalidaException(String motivo) {
        super(HttpStatus.CONFLICT);
        setDetail("Solicitação de afastamento inválida: " + motivo);
    }
}
