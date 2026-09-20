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

    @Test
    void nascimentoLimiteDaMaioridade_ehOMesmoDiaHaDezoitoAnos() {
        assertThat(Pessoa.nascimentoLimiteDaMaioridade(HOJE)).isEqualTo(LocalDate.of(2008, 9, 20));
    }

    /**
     * O filtro por faixa etária roda no banco comparando com o limite; precisa concordar com {@code menorDeIdade}
     * em todos os dias perto da fronteira, inclusive com 29 de fevereiro.
     */
    @Test
    void nascimentoLimiteDaMaioridade_concordaComMenorDeIdadeEmTodosOsDiasPertoDaFronteira() {
        LocalDate[] hojes = {
                HOJE,
                LocalDate.of(2028, 2, 29),
                LocalDate.of(2029, 2, 28),
                LocalDate.of(2029, 3, 1),
                LocalDate.of(2030, 2, 28),
                LocalDate.of(2031, 1, 1),
        };
        for (LocalDate hoje : hojes) {
            LocalDate limite = Pessoa.nascimentoLimiteDaMaioridade(hoje);
            for (LocalDate nascimento = hoje.minusYears(19); !nascimento.isAfter(hoje.minusYears(17));
                 nascimento = nascimento.plusDays(1)) {
                boolean menorPeloLimite = nascimento.isAfter(limite);
                assertThat(menorPeloLimite)
                        .as("nascido em %s, hoje %s", nascimento, hoje)
                        .isEqualTo(nascidaEm(nascimento).menorDeIdade(hoje));
            }
        }
    }
}
