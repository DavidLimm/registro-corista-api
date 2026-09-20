package com.registraai.registro_coristas_api.corista.dto;

import com.registraai.registro_coristas_api.corista.model.Corista;
import com.registraai.registro_coristas_api.corista.model.ListaClassificacao;
import com.registraai.registro_coristas_api.corista.model.TamanhoCamisa;
import com.registraai.registro_coristas_api.corista.model.TipoVoz;
import com.registraai.registro_coristas_api.pessoa.dto.PessoaResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CoristaResponse(
        UUID id,
        PessoaResponse pessoa,
        TipoVoz tipoVoz,
        TamanhoCamisa tamanhoCamisa,
        String ocupacao,
        ListaClassificacao listaClassificacao,
        UUID promovidoPor,
        Instant promovidoEm,
        Instant criadoEm,
        Instant atualizadoEm
) {

    public static CoristaResponse de(Corista corista, LocalDate hoje) {
        return new CoristaResponse(
                corista.getId(),
                PessoaResponse.de(corista.getPessoa(), hoje),
                corista.getTipoVoz(),
                corista.getTamanhoCamisa(),
                corista.getOcupacao(),
                corista.getListaClassificacao(),
                corista.getPromovidoPor(),
                corista.getPromovidoEm(),
                corista.getCriadoEm(),
                corista.getAtualizadoEm()
        );
    }
}
