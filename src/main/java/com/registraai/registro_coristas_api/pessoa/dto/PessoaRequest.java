package com.registraai.registro_coristas_api.pessoa.dto;

import com.registraai.registro_coristas_api.endereco.dto.EnderecoRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Dados comuns a qualquer pessoa. O status nunca vem do cliente: todo cadastro entra como {@code PENDENTE}.
 * Para menores de 18 anos o Service exige responsável legal (nome e telefone) e {@code consentimentoLgpd = true}.
 * Na edição, {@code endereco} ausente mantém o endereço atual.
 */
public record PessoaRequest(

        @NotBlank @Size(max = 150)
        String nome,

        @NotNull @Past
        LocalDate dataNascimento,

        @NotNull
        UUID congregacaoId,

        @Valid
        EnderecoRequest endereco,

        @Size(max = 150)
        String responsavelLegalNome,

        // aceita "81999990000", "(81) 99999-0000", "8133334444"; o Service grava só os dígitos
        @Pattern(regexp = "^\\(?\\d{2}\\)?\\s?9?\\d{4}-?\\d{4}$",
                message = "deve ser um telefone com DDD, ex.: (81) 99999-0000")
        String responsavelLegalTelefone,

        Boolean consentimentoLgpd
) {
}
