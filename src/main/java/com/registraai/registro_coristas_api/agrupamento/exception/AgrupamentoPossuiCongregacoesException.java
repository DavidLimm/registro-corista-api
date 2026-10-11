package com.registraai.registro_coristas_api.agrupamento.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Renderizada como Problem Details (RFC 7807) com status 409. */
public class AgrupamentoPossuiCongregacoesException extends ErrorResponseException {

    public AgrupamentoPossuiCongregacoesException(String descricao, long quantidade) {
        super(HttpStatus.CONFLICT);
        String situacao = quantidade == 1
                ? "existe 1 congregação ativa vinculada"
                : "existem " + quantidade + " congregações ativas vinculadas";
        setDetail("Não é possível inativar " + descricao + ": " + situacao
                + ". Remaneje-as antes de inativar.");
    }
}
