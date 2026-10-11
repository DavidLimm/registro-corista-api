package com.registraai.registro_coristas_api.afastamento.model;

public enum StatusSolicitacao {

    PENDENTE,

    /** O afastamento foi aprovado e a pessoa passou a {@code AFASTADO}. */
    APROVADA,

    NEGADA
}
