package com.registraai.registro_coristas_api.pessoa.dto;

import com.registraai.registro_coristas_api.pessoa.model.FaixaEtaria;
import com.registraai.registro_coristas_api.pessoa.model.StatusPessoa;

import java.util.UUID;

/**
 * Filtros opcionais da listagem (query params); todos {@code null} lista tudo. {@code nome} busca por trecho.
 * {@code faixaEtaria} é a faixa real, derivada da data de nascimento (não a lista de classificação do corista).
 */
public record PessoaFiltro(
        String nome,
        UUID areaId,
        UUID congregacaoId,
        FaixaEtaria faixaEtaria,
        StatusPessoa status
) {
}
