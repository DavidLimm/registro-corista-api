package com.registraai.registro_coristas_api.usuario.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

/** Substitui o escopo do usuário pelo conjunto informado (vazio tira todos os vínculos). */
public record EscopoRequest(

        @NotNull
        Set<@NotNull UUID> agrupamentoIds
) {
}
