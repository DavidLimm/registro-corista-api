package com.registraai.registro_coristas_api.agrupamento.model;

/** Tipos fixos de agrupamento; as instâncias (Área 40, Setor 1...) são linhas criadas pelo sistema. */
public enum TipoAgrupamento {
    /** Nome + país; subdivisões são "Locais" com nome. */
    CAMPO_MISSIONARIO,
    /** Nome; subdivisões são Áreas com número. */
    FILIAL,
    /** Número + nome; subdivisões são Áreas com número. */
    SETOR,
    /** Apenas número; tem uma única subdivisão implícita (sem número/nome). */
    AREA
}
