package com.registraai.registro_coristas_api.area.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AreaRequest(

        @NotNull @Positive
        Integer numero,

        @NotBlank @Size(max = 100)
        String nome
) {
}
