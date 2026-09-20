package com.registraai.registro_coristas_api.pessoa.model;

/**
 * Workflow de cadastro: self-service entra como {@link #PENDENTE}, {@code PRESBITERO+} aprova ({@link #APROVADO}).
 * Remoção é soft delete ({@link #INATIVO}), nunca DELETE físico.
 */
public enum StatusPessoa {
    PENDENTE,
    APROVADO,
    INATIVO
}
