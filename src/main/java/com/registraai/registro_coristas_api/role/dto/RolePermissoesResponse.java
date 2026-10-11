package com.registraai.registro_coristas_api.role.dto;

import java.util.Set;
import java.util.UUID;

public record RolePermissoesResponse(UUID roleId, String nome, Set<String> permissoes) {
}
