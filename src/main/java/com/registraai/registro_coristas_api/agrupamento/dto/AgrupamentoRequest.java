package com.registraai.registro_coristas_api.agrupamento.dto;

import com.registraai.registro_coristas_api.agrupamento.model.TipoAgrupamento;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Quais campos valem depende do tipo; essa regra é do Service. */
public record AgrupamentoRequest(

        @NotNull
        TipoAgrupamento tipo,

        @Size(max = 150)
        String nome,

        @Positive
        Integer numero,

        @Size(max = 100)
        String pais
) {
}
