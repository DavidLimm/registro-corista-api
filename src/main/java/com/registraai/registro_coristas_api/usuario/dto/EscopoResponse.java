package com.registraai.registro_coristas_api.usuario.dto;

import com.registraai.registro_coristas_api.agrupamento.dto.AgrupamentoResponse;

import java.util.List;
import java.util.UUID;

/**
 * Agrupamentos a que o usuário tem acesso. {@code global} é verdadeiro para ADMIN e PASTOR_ADMIN: eles enxergam
 * tudo e não têm vínculos.
 */
public record EscopoResponse(
        UUID usuarioId,
        boolean global,
        List<AgrupamentoResponse> agrupamentos
) {
}
