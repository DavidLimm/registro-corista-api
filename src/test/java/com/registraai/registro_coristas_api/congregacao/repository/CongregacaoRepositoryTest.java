package com.registraai.registro_coristas_api.congregacao.repository;

import com.registraai.registro_coristas_api.agrupamento.model.Agrupamento;
import com.registraai.registro_coristas_api.agrupamento.model.Subdivisao;
import com.registraai.registro_coristas_api.agrupamento.model.TipoAgrupamento;
import com.registraai.registro_coristas_api.agrupamento.repository.AgrupamentoRepository;
import com.registraai.registro_coristas_api.agrupamento.repository.SubdivisaoRepository;
import com.registraai.registro_coristas_api.congregacao.model.Congregacao;
import com.registraai.registro_coristas_api.suporte.HierarquiaTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Valida o mapeamento de {@link Congregacao} contra as migrations ({@code ddl-auto: validate}), a unicidade do nome
 * por subdivisão e as contagens usadas pela regra "não inativar com congregação ativa".
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CongregacaoRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private CongregacaoRepository congregacaoRepository;

    @Autowired
    private AgrupamentoRepository agrupamentoRepository;

    @Autowired
    private SubdivisaoRepository subdivisaoRepository;

    private Subdivisao novaSubdivisao() {
        return HierarquiaTeste.salvarSubdivisao(agrupamentoRepository, subdivisaoRepository);
    }

    private Congregacao salvar(Subdivisao subdivisao, String nome, boolean ativa) {
        return congregacaoRepository.saveAndFlush(Congregacao.builder().subdivisao(subdivisao).nome(nome).ativa(ativa).build());
    }

    @Test
    void salvar_geraIdTimestampsEAtivaPorPadrao() {
        Congregacao salva = congregacaoRepository.saveAndFlush(
                Congregacao.builder().subdivisao(novaSubdivisao()).nome("Sede").build());

        assertThat(salva.getId()).isNotNull();
        assertThat(salva.getCriadoEm()).isNotNull();
        assertThat(salva.isAtiva()).isTrue();
        assertThat(salva.getEndereco()).isNull();
    }

    @Test
    void countBySubdivisaoIdAndAtivaTrue_contaSoAtivasDaSubdivisaoInformada() {
        Subdivisao a = novaSubdivisao();
        Subdivisao b = novaSubdivisao();
        salvar(a, "Sede", true);
        salvar(a, "Bairro Novo", true);
        salvar(a, "Fechada", false);
        salvar(b, "Outra", true);

        assertThat(congregacaoRepository.countBySubdivisaoIdAndAtivaTrue(a.getId())).isEqualTo(2);
        assertThat(congregacaoRepository.countBySubdivisaoIdAndAtivaTrue(b.getId())).isEqualTo(1);
    }

    @Test
    void countBySubdivisaoIdAndAtivaTrue_retornaZeroQuandoSoHaInativasOuNaoHaNenhuma() {
        Subdivisao comInativa = novaSubdivisao();
        Subdivisao vazia = novaSubdivisao();
        salvar(comInativa, "Fechada", false);

        assertThat(congregacaoRepository.countBySubdivisaoIdAndAtivaTrue(comInativa.getId())).isZero();
        assertThat(congregacaoRepository.countBySubdivisaoIdAndAtivaTrue(vazia.getId())).isZero();
    }

    @Test
    void contarAtivasPorAgrupamento_somaTodasAsSubdivisoesDoAgrupamento() {
        Agrupamento filial = agrupamentoRepository.saveAndFlush(
                Agrupamento.builder().tipo(TipoAgrupamento.FILIAL).nome("Filial com duas áreas").build());
        Subdivisao area1 = subdivisaoRepository.saveAndFlush(Subdivisao.builder().agrupamento(filial).numero(1).build());
        Subdivisao area2 = subdivisaoRepository.saveAndFlush(Subdivisao.builder().agrupamento(filial).numero(2).build());
        salvar(area1, "Sede", true);
        salvar(area2, "Bairro", true);
        salvar(area2, "Fechada", false);
        salvar(novaSubdivisao(), "De outro agrupamento", true);

        assertThat(congregacaoRepository.contarAtivasPorAgrupamento(filial.getId())).isEqualTo(2);
    }

    @Test
    void salvar_mesmoNomeNaMesmaSubdivisaoViolaUnicidadeDoBanco() {
        Subdivisao subdivisao = novaSubdivisao();
        salvar(subdivisao, "Sede", true);

        assertThatThrownBy(() -> salvar(subdivisao, "Sede", true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_congregacao_subdivisao_nome");
    }

    @Test
    void salvar_mesmoNomeEmSubdivisoesDiferentesEPermitido() {
        salvar(novaSubdivisao(), "Sede", true);

        assertThat(salvar(novaSubdivisao(), "Sede", true).getId()).isNotNull();
    }

    @Test
    void existsBySubdivisaoIdAndNomeIgnoreCase_ignoraMaiusculasEEscopoDaSubdivisao() {
        Subdivisao a = novaSubdivisao();
        Subdivisao b = novaSubdivisao();
        salvar(a, "Sede Central", true);

        assertThat(congregacaoRepository.existsBySubdivisaoIdAndNomeIgnoreCase(a.getId(), "sede central")).isTrue();
        assertThat(congregacaoRepository.existsBySubdivisaoIdAndNomeIgnoreCase(a.getId(), "Outra")).isFalse();
        assertThat(congregacaoRepository.existsBySubdivisaoIdAndNomeIgnoreCase(b.getId(), "Sede Central")).isFalse();
    }

    @Test
    void existsBySubdivisaoIdAndNomeIgnoreCaseAndIdNot_desconsideraOProprioRegistro() {
        Subdivisao subdivisao = novaSubdivisao();
        Congregacao sede = salvar(subdivisao, "Sede", true);
        Congregacao filial = salvar(subdivisao, "Filial", true);

        assertThat(congregacaoRepository.existsBySubdivisaoIdAndNomeIgnoreCaseAndIdNot(subdivisao.getId(), "SEDE", sede.getId())).isFalse();
        assertThat(congregacaoRepository.existsBySubdivisaoIdAndNomeIgnoreCaseAndIdNot(subdivisao.getId(), "SEDE", filial.getId())).isTrue();
    }
}
