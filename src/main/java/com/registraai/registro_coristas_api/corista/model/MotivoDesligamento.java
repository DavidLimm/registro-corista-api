package com.registraai.registro_coristas_api.corista.model;

/**
 * Por que um cadastro de corista foi desligado (desativado, sem apagar). Quem sai ou passa da idade continua sendo
 * {@code INATIVO} na pessoa; isto cobre o que não mexe no status da pessoa.
 */
public enum MotivoDesligamento {

    /** A pessoa recebeu um papel de liderança/apoio: nenhum corista é líder, então o cadastro de corista fecha. */
    VIROU_LIDER
}
