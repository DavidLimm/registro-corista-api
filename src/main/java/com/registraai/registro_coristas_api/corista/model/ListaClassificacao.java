package com.registraai.registro_coristas_api.corista.model;

/**
 * Lista em que o corista está classificado. Por padrão segue a idade (adolescente até 17a 11m 30d; jovem a partir
 * de 18a), mas é um campo gravado: só o {@code LIDER_MOCIDADE} pode promover um adolescente para {@link #JOVEM}
 * antes da idade, de forma permanente e com trilha ({@code promovidoPor}/{@code promovidoEm}). Nunca o contrário.
 */
public enum ListaClassificacao {
    ADOLESCENTE,
    JOVEM
}
