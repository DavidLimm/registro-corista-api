package com.registraai.registro_coristas_api.congregacao.repository;

import com.registraai.registro_coristas_api.area.model.Area;
import com.registraai.registro_coristas_api.area.repository.AreaRepository;
import com.registraai.registro_coristas_api.congregacao.model.Congregacao;
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
 * Valida o mapeamento de {@link Congregacao} contra a migration V3 ({@code ddl-auto: validate}) e a contagem
 * usada pela regra "não inativar área com congregação ativa".
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
    private AreaRepository areaRepository;

    private Congregacao salvar(Area area, String nome, boolean ativa) {
        return congregacaoRepository.saveAndFlush(Congregacao.builder().area(area).nome(nome).ativa(ativa).build());
    }

    @Test
    void salvar_geraIdTimestampsEAtivaPorPadrao() {
        Area area = areaRepository.saveAndFlush(Area.builder().numero(40).nome("Área 40").build());

        Congregacao salva = congregacaoRepository.saveAndFlush(Congregacao.builder().area(area).nome("Sede").build());

        assertThat(salva.getId()).isNotNull();
        assertThat(salva.getCriadoEm()).isNotNull();
        assertThat(salva.isAtiva()).isTrue();
        assertThat(salva.getEndereco()).isNull();
    }

    @Test
    void countByAreaIdAndAtivaTrue_contaSoAtivasDaAreaInformada() {
        Area area40 = areaRepository.saveAndFlush(Area.builder().numero(40).nome("Área 40").build());
        Area area41 = areaRepository.saveAndFlush(Area.builder().numero(41).nome("Área 41").build());
        salvar(area40, "Sede", true);
        salvar(area40, "Bairro Novo", true);
        salvar(area40, "Fechada", false);
        salvar(area41, "Outra área", true);

        assertThat(congregacaoRepository.countByAreaIdAndAtivaTrue(area40.getId())).isEqualTo(2);
        assertThat(congregacaoRepository.countByAreaIdAndAtivaTrue(area41.getId())).isEqualTo(1);
    }

    @Test
    void countByAreaIdAndAtivaTrue_retornaZeroQuandoSoHaInativasOuNaoHaNenhuma() {
        Area comInativa = areaRepository.saveAndFlush(Area.builder().numero(50).nome("Área 50").build());
        Area vazia = areaRepository.saveAndFlush(Area.builder().numero(51).nome("Área 51").build());
        salvar(comInativa, "Fechada", false);

        assertThat(congregacaoRepository.countByAreaIdAndAtivaTrue(comInativa.getId())).isZero();
        assertThat(congregacaoRepository.countByAreaIdAndAtivaTrue(vazia.getId())).isZero();
    }

    @Test
    void salvar_mesmoNomeNaMesmaAreaViolaUnicidadeDoBanco() {
        Area area = areaRepository.saveAndFlush(Area.builder().numero(60).nome("Área 60").build());
        salvar(area, "Sede", true);

        assertThatThrownBy(() -> salvar(area, "Sede", true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_congregacao_area_nome");
    }

    @Test
    void salvar_mesmoNomeEmAreasDiferentesEPermitido() {
        Area area61 = areaRepository.saveAndFlush(Area.builder().numero(61).nome("Área 61").build());
        Area area62 = areaRepository.saveAndFlush(Area.builder().numero(62).nome("Área 62").build());

        salvar(area61, "Sede", true);

        assertThat(salvar(area62, "Sede", true).getId()).isNotNull();
    }

    @Test
    void existsByAreaIdAndNomeIgnoreCase_ignoraMaiusculasEEscopoDaArea() {
        Area area63 = areaRepository.saveAndFlush(Area.builder().numero(63).nome("Área 63").build());
        Area area64 = areaRepository.saveAndFlush(Area.builder().numero(64).nome("Área 64").build());
        salvar(area63, "Sede Central", true);

        assertThat(congregacaoRepository.existsByAreaIdAndNomeIgnoreCase(area63.getId(), "sede central")).isTrue();
        assertThat(congregacaoRepository.existsByAreaIdAndNomeIgnoreCase(area63.getId(), "Outra")).isFalse();
        assertThat(congregacaoRepository.existsByAreaIdAndNomeIgnoreCase(area64.getId(), "Sede Central")).isFalse();
    }

    @Test
    void existsByAreaIdAndNomeIgnoreCaseAndIdNot_desconsideraOProprioRegistro() {
        Area area = areaRepository.saveAndFlush(Area.builder().numero(65).nome("Área 65").build());
        Congregacao sede = salvar(area, "Sede", true);
        Congregacao filial = salvar(area, "Filial", true);

        assertThat(congregacaoRepository.existsByAreaIdAndNomeIgnoreCaseAndIdNot(area.getId(), "SEDE", sede.getId())).isFalse();
        assertThat(congregacaoRepository.existsByAreaIdAndNomeIgnoreCaseAndIdNot(area.getId(), "SEDE", filial.getId())).isTrue();
    }
}
