package com.registraai.registro_coristas_api.corista.dto;

import com.registraai.registro_coristas_api.corista.model.TipoVoz;
import jakarta.validation.constraints.NotNull;

/** Troca só o tipo de voz do corista (o que o maestro pode editar). */
public record CoristaVozRequest(
        @NotNull
        TipoVoz tipoVoz
) {
}
