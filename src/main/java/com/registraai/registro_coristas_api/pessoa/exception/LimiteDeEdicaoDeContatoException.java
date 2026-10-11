package com.registraai.registro_coristas_api.pessoa.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Renderizada como Problem Details (RFC 7807) com status 429. */
public class LimiteDeEdicaoDeContatoException extends ErrorResponseException {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")
            .withZone(ZoneId.of("America/Recife"));

    public LimiteDeEdicaoDeContatoException(String oQue, Instant proximaEdicao) {
        super(HttpStatus.TOO_MANY_REQUESTS);
        setDetail("Você já alterou " + oQue + " há pouco tempo. Uma nova alteração é permitida a partir de "
                + FORMATO.format(proximaEdicao) + ". Em caso de urgência, procure o líder da sua mocidade.");
    }
}
