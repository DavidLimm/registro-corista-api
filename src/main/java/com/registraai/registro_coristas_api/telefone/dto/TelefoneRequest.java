package com.registraai.registro_coristas_api.telefone.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Cadastro e edição de telefone. {@code principal = true} torna este o telefone principal da pessoa (o anterior
 * deixa de ser). {@code false} ou ausente não altera: o primeiro telefone da pessoa é sempre o principal, e o
 * principal só muda quando outro é marcado como tal.
 */
public record TelefoneRequest(

        // aceita "81999990000", "(81) 99999-0000", "8133334444"; o Service grava só os dígitos
        @NotBlank
        @Pattern(regexp = "^\\(?\\d{2}\\)?\\s?9?\\d{4}-?\\d{4}$",
                message = "deve ser um telefone com DDD, ex.: (81) 99999-0000")
        String numero,

        Boolean whatsapp,

        Boolean principal
) {
}
