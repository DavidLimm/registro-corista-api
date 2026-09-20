package com.registraai.registro_coristas_api.endereco.dto;

import com.registraai.registro_coristas_api.endereco.model.Endereco;

import java.time.Instant;
import java.util.UUID;

public record EnderecoResponse(
        UUID id,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String uf,
        String cep,
        Instant criadoEm,
        Instant atualizadoEm
) {

    public static EnderecoResponse de(Endereco endereco) {
        return new EnderecoResponse(
                endereco.getId(),
                endereco.getLogradouro(),
                endereco.getNumero(),
                endereco.getComplemento(),
                endereco.getBairro(),
                endereco.getCidade(),
                endereco.getUf(),
                endereco.getCep(),
                endereco.getCriadoEm(),
                endereco.getAtualizadoEm()
        );
    }
}
