package com.registraai.registro_coristas_api.area.repository;

import com.registraai.registro_coristas_api.area.model.Area;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sobe o schema real via Flyway num Postgres 16 (Testcontainers) com {@code ddl-auto: validate}:
 * se o mapeamento de {@link Area} divergir da migration V2, o contexto nem sobe.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AreaRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private AreaRepository areaRepository;

    @Test
    void salvar_geraIdTimestampsEAtivaPorPadrao() {
        Area salva = areaRepository.saveAndFlush(Area.builder().numero(40).nome("Área 40").build());

        assertThat(salva.getId()).isNotNull();
        assertThat(salva.getCriadoEm()).isNotNull();
        assertThat(salva.getAtualizadoEm()).isNotNull();
        assertThat(areaRepository.findById(salva.getId())).get().extracting(Area::isAtiva).isEqualTo(true);
    }

    @Test
    void salvar_numeroRepetidoViolaUnicidadeDoBanco() {
        areaRepository.saveAndFlush(Area.builder().numero(40).nome("Área 40").build());

        assertThatThrownBy(() -> areaRepository.saveAndFlush(Area.builder().numero(40).nome("Outra").build()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_area_numero");
    }

    @Test
    void salvar_numeroNaoPositivoViolaConstraintDoBanco() {
        assertThatThrownBy(() -> areaRepository.saveAndFlush(Area.builder().numero(0).nome("Zero").build()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_area_numero_positivo");
    }

    @Test
    void existsByNumero_eExistsByNumeroAndIdNot() {
        Area area40 = areaRepository.saveAndFlush(Area.builder().numero(40).nome("Área 40").build());
        Area area1 = areaRepository.saveAndFlush(Area.builder().numero(1).nome("Matriz").build());

        assertThat(areaRepository.existsByNumero(40)).isTrue();
        assertThat(areaRepository.existsByNumero(99)).isFalse();
        // o próprio registro não conta como duplicado; o de outra área conta
        assertThat(areaRepository.existsByNumeroAndIdNot(40, area40.getId())).isFalse();
        assertThat(areaRepository.existsByNumeroAndIdNot(40, area1.getId())).isTrue();
    }

    @Test
    void listar_ordenaPorNumeroEFiltraPorSituacao() {
        areaRepository.saveAndFlush(Area.builder().numero(40).nome("Área 40").build());
        areaRepository.saveAndFlush(Area.builder().numero(1).nome("Matriz").build());
        areaRepository.saveAndFlush(Area.builder().numero(2).nome("Antiga").ativa(false).build());

        assertThat(areaRepository.findAllByOrderByNumeroAsc())
                .extracting(Area::getNumero).containsExactly(1, 2, 40);
        assertThat(areaRepository.findAllByAtivaOrderByNumeroAsc(true))
                .extracting(Area::getNumero).containsExactly(1, 40);
        List<Area> inativas = areaRepository.findAllByAtivaOrderByNumeroAsc(false);
        assertThat(inativas).extracting(Area::getNumero).containsExactly(2);
    }
}
