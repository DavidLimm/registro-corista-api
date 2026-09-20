package com.registraai.registro_coristas_api.pessoa.dto;

import com.registraai.registro_coristas_api.congregacao.dto.CongregacaoResumoResponse;
import com.registraai.registro_coristas_api.endereco.dto.EnderecoResponse;
import com.registraai.registro_coristas_api.pessoa.model.FaixaEtaria;
import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.pessoa.model.StatusPessoa;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PessoaResponse(
        UUID id,
        String nome,
        LocalDate dataNascimento,
        int idade,
        FaixaEtaria faixaEtaria,
        StatusPessoa status,
        CongregacaoResumoResponse congregacao,
        EnderecoResponse endereco,
        String responsavelLegalNome,
        String responsavelLegalTelefone,
        Instant consentimentoLgpdEm,
        UUID aprovadoPor,
        Instant aprovadoEm,
        Instant criadoEm,
        Instant atualizadoEm
) {

    /** {@code idade} e {@code faixaEtaria} são calculadas para {@code hoje}; nunca ficam gravadas. */
    public static PessoaResponse de(Pessoa pessoa, LocalDate hoje) {
        return new PessoaResponse(
                pessoa.getId(),
                pessoa.getNome(),
                pessoa.getDataNascimento(),
                pessoa.idade(hoje),
                pessoa.faixaEtaria(hoje),
                pessoa.getStatus(),
                CongregacaoResumoResponse.de(pessoa.getCongregacao()),
                pessoa.getEndereco() == null ? null : EnderecoResponse.de(pessoa.getEndereco()),
                pessoa.getResponsavelLegalNome(),
                pessoa.getResponsavelLegalTelefone(),
                pessoa.getConsentimentoLgpdEm(),
                pessoa.getAprovadoPor(),
                pessoa.getAprovadoEm(),
                pessoa.getCriadoEm(),
                pessoa.getAtualizadoEm()
        );
    }
}
