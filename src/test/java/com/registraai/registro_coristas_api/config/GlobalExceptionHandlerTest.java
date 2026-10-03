package com.registraai.registro_coristas_api.config;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void violacaoDeIntegridade_vira409ComProblemDetails() {
        ProblemDetail problemDetail = handler.tratarViolacaoDeIntegridade(
                new DataIntegrityViolationException("uk_usuario_email"));

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problemDetail.getDetail()).isEqualTo("O registro já existe ou viola uma restrição do banco de dados.");
    }
}
