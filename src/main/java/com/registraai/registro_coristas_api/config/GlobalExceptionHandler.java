package com.registraai.registro_coristas_api.config;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Rede de segurança para violações de integridade que escapam das validações "existe?"/"já existe?" feitas no
 * Service antes de gravar (ex.: duas requisições concorrentes passam pela checagem antes de qualquer uma
 * commitar, e a segunda esbarra numa constraint única do Postgres). Sem isto, a exceção do driver JDBC não é um
 * {@link org.springframework.web.ErrorResponseException} e vira um 500 sem Problem Details.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail tratarViolacaoDeIntegridade(DataIntegrityViolationException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problemDetail.setDetail("O registro já existe ou viola uma restrição do banco de dados.");
        return problemDetail;
    }
}
