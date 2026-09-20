package com.registraai.registro_coristas_api.telefone.dto;

import com.registraai.registro_coristas_api.telefone.model.Telefone;

import java.time.Instant;
import java.util.UUID;

public record TelefoneResponse(
        UUID id,
        UUID pessoaId,
        String numero,
        boolean whatsapp,
        boolean principal,
        Instant criadoEm,
        Instant atualizadoEm
) {

    public static TelefoneResponse de(Telefone telefone) {
        return new TelefoneResponse(
                telefone.getId(),
                telefone.getPessoa().getId(),
                telefone.getNumero(),
                telefone.isWhatsapp(),
                telefone.isPrincipal(),
                telefone.getCriadoEm(),
                telefone.getAtualizadoEm()
        );
    }
}
