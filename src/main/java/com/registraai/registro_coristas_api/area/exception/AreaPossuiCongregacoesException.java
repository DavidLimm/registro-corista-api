package com.registraai.registro_coristas_api.area.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Renderizada como Problem Details (RFC 7807) com status 409. */
public class AreaPossuiCongregacoesException extends ErrorResponseException {

    public AreaPossuiCongregacoesException(Integer numeroArea, long quantidade) {
        super(HttpStatus.CONFLICT);
        String situacao = quantidade == 1
                ? "existe 1 congregação ativa vinculada"
                : "existem " + quantidade + " congregações ativas vinculadas";
        setDetail("Não é possível inativar a área " + numeroArea + ": " + situacao
                + ". Remaneje-as para outra área antes de inativar.");
    }
}
