package com.registraai.registro_coristas_api.pessoa.model;

/**
 * Faixa etária <b>real</b>, sempre derivada da data de nascimento e nunca gravada: adolescente até 17a 11m 30d,
 * jovem a partir dos 18 anos. Não confundir com a lista de classificação do corista, que é gravada.
 */
public enum FaixaEtaria {
    ADOLESCENTE,
    JOVEM
}
