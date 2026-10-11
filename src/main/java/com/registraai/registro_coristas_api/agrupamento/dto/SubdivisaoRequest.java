package com.registraai.registro_coristas_api.agrupamento.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Campo missionário: só nome. Filial e setor: só número. A regra por tipo é do Service. */
public record SubdivisaoRequest(

        @Positive
        Integer numero,

        @Size(max = 150)
        String nome
) {
}
