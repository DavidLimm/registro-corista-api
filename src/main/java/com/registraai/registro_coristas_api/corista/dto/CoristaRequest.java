package com.registraai.registro_coristas_api.corista.dto;

import com.registraai.registro_coristas_api.corista.model.TamanhoCamisa;
import com.registraai.registro_coristas_api.corista.model.TipoVoz;
import com.registraai.registro_coristas_api.pessoa.dto.PessoaRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cadastro e edição de corista numa única requisição: {@code pessoa} traz os dados comuns e o restante os campos
 * exclusivos de corista. A lista de classificação não vem do cliente: nasce conforme a idade.
 */
public record CoristaRequest(

        @NotNull @Valid
        PessoaRequest pessoa,

        @NotNull
        TipoVoz tipoVoz,

        @NotNull
        TamanhoCamisa tamanhoCamisa,

        @Size(max = 100)
        String ocupacao
) {
}
