package com.registraai.registro_coristas_api.autenticacao.service;

import java.time.Instant;

/** O que o JWT validado informa: de quem é ({@code email}) e quando foi emitido (precisão de segundos). */
public record TokenValido(String email, Instant emitidoEm) {
}
