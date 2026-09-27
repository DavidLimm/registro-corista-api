package com.registraai.registro_coristas_api.pessoa.model;

/**
 * Workflow de cadastro: self-service entra como {@link #PENDENTE}. Os líderes de cada recorte (adolescentes:
 * {@code DIRIGENTE_UNIAO}; jovens: {@code LIDERANCA_GRUPO_JOVEM}/{@code DIRIGENTE_CAMPANHA}; e os demais papéis de
 * liderança) aprovam para {@link #APROVADO} ou rejeitam para {@link #REPROVADO}. Essa aprovação nunca muda a
 * classificação adolescente/jovem do corista — isso é sempre a promoção antecipada (só {@code LIDER_MOCIDADE},
 * só ADOLESCENTE → JOVEM, permanente). Remoção é soft delete ({@link #INATIVO}), nunca DELETE físico.
 */
public enum StatusPessoa {
    PENDENTE,
    APROVADO,
    /** Cadastro rejeitado: nunca chegou a ser aprovado (diferente de {@link #INATIVO}, que é remoção). */
    REPROVADO,
    INATIVO
}
