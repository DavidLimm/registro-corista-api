package com.registraai.registro_coristas_api.corista.dto;

import com.registraai.registro_coristas_api.corista.model.ListaClassificacao;
import com.registraai.registro_coristas_api.pessoa.model.StatusPessoa;

import java.util.UUID;

/** Filtros opcionais da listagem (query params); todos {@code null} lista tudo. {@code nome} busca por trecho. */
public record CoristaFiltro(
        String nome,
        UUID areaId,
        UUID congregacaoId,
        ListaClassificacao listaClassificacao,
        StatusPessoa status
) {
}
