package com.registraai.registro_coristas_api.endereco.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dados de endereço enviados pelo front. Logradouro, bairro, cidade, uf e cep vêm da consulta de CEP feita
 * no front; {@code numero} e {@code complemento} são digitados pelo usuário (a API de CEP não os fornece).
 */
public record EnderecoRequest(

        @NotBlank @Size(max = 150)
        String logradouro,

        @Size(max = 10)
        String numero,

        @Size(max = 100)
        String complemento,

        @NotBlank @Size(max = 100)
        String bairro,

        @NotBlank @Size(max = 100)
        String cidade,

        @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "deve ter 2 letras")
        String uf,

        // aceita "50030-230" ou "50030230"; o Service grava só os dígitos. Opcional (ex.: zona rural sem CEP)
        @Pattern(regexp = "^(\\d{8}|\\d{5}-\\d{3})$", message = "deve estar no formato 00000-000 ou 00000000")
        String cep
) {
}
