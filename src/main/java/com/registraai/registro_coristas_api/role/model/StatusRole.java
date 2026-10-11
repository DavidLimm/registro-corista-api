package com.registraai.registro_coristas_api.role.model;

/** Situação de um papel no fluxo de solicitação. Os papéis criados diretamente (ou antigos) já nascem {@link #APROVADA}. */
public enum StatusRole {

    /** Pedido feito, aguardando o PASTOR. O papel fica inativo e não pode ser atribuído. */
    PENDENTE,

    APROVADA,

    /** Pedido recusado. Diferente de um papel aprovado e depois inativado. */
    REPROVADA
}
