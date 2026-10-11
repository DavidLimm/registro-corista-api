package com.registraai.registro_coristas_api.autenticacao.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Respostas 401 (não autenticado) e 403 (sem permissão) em Problem Details (RFC 7807), no mesmo formato do restante
 * da API. Essas exceções nascem dentro da cadeia do Security e nunca chegam aos {@code @RestControllerAdvice},
 * por isso o JSON é escrito aqui. Os textos são fixos (nada que o cliente enviou é refletido na resposta).
 */
@Component
public class ProblemDetailSeguranca implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        String detalhe = exception instanceof BadCredentialsException
                ? "E-mail ou senha inválidos."
                : "É necessário estar autenticado para acessar este recurso.";
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        escrever(response, HttpStatus.UNAUTHORIZED, detalhe);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        escrever(response, HttpStatus.FORBIDDEN, "Você não tem permissão para realizar esta ação.");
    }

    private void escrever(HttpServletResponse response, HttpStatus status, String detalhe) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase()
                + "\",\"status\":" + status.value() + ",\"detail\":\"" + detalhe + "\"}");
    }
}
