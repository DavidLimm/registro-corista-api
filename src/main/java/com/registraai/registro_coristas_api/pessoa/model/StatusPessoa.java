package com.registraai.registro_coristas_api.pessoa.model;

/**
 * Workflow de cadastro: self-service entra como {@link #PENDENTE}; aprovação por {@code PRESBITERO} (ou papel
 * superior) leva a {@link #APROVADO}. Remoção é soft delete ({@link #INATIVO}), nunca DELETE físico.
 */
public enum StatusPessoa {
    PENDENTE,
    APROVADO,
    INATIVO
}
