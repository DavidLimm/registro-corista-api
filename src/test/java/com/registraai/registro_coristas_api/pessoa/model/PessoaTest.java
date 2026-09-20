package com.registraai.registro_coristas_api.pessoa.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PessoaTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 20);

    private Pessoa nascidaEm(LocalDate nascimento) {
        return Pessoa.builder().dataNascimento(nascimento).build();
    }

    @Test
    void idade_contaAnosCompletos() {
        assertThat(nascidaEm(LocalDate.of(2000, 9, 20)).idade(HOJE)).isEqualTo(26);
        assertThat(nascidaEm(LocalDate.of(2000, 9, 21)).idade(HOJE)).isEqualTo(25);
        assertThat(nascidaEm(LocalDate.of(2010, 3, 1)).idade(HOJE)).isEqualTo(16);
    }

    @Test
    void faixaEtaria_dezessete_onzeMeses_trintaDias_aindaEAdolescente() {
        // 17a 11m 30d: falta 1 dia para completar 18 anos
        Pessoa pessoa = nascidaEm(LocalDate.of(2008, 9, 21));

        assertThat(pessoa.faixaEtaria(HOJE)).isEqualTo(FaixaEtaria.ADOLESCENTE);
        assertThat(pessoa.menorDeIdade(HOJE)).isTrue();
    }

    @Test
    void faixaEtaria_noDiaDosDezoitoAnosJaEJovem() {
        Pessoa pessoa = nascidaEm(LocalDate.of(2008, 9, 20));

        assertThat(pessoa.faixaEtaria(HOJE)).isEqualTo(FaixaEtaria.JOVEM);
        assertThat(pessoa.menorDeIdade(HOJE)).isFalse();
    }

    @Test
    void faixaEtaria_depoisDosDezoitoEJovem() {
        assertThat(nascidaEm(LocalDate.of(2008, 9, 19)).faixaEtaria(HOJE)).isEqualTo(FaixaEtaria.JOVEM);
        assertThat(nascidaEm(LocalDate.of(1990, 1, 1)).faixaEtaria(HOJE)).isEqualTo(FaixaEtaria.JOVEM);
    }

    @Test
    void faixaEtaria_criancaEAdolescente() {
        assertThat(nascidaEm(LocalDate.of(2020, 1, 1)).faixaEtaria(HOJE)).isEqualTo(FaixaEtaria.ADOLESCENTE);
    }
}
