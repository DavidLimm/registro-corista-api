package com.registraai.registro_coristas_api.autenticacao.dto;

import java.util.List;

public record LoginResponse(
        String token, String email, List<String> roles) {
}
