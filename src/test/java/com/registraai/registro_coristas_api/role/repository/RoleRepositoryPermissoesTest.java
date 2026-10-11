package com.registraai.registro_coristas_api.role.repository;

import com.registraai.registro_coristas_api.role.model.Permissao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida a matriz semeada pela V17/V18 contra o banco real (Flyway + Postgres 16 via Testcontainers). Se alguém
 * criar uma permissão no enum e esquecer a migration, ou vice-versa, este teste falha.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RoleRepositoryPermissoesTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void presbitero_temExatamenteAsTresPermissoesDaMatriz() {
        assertThat(nomesDe("PRESBITERO"))
                .containsExactlyInAnyOrder("DASHBOARD_LER", "CORISTA_LER", "CORISTA_SOLICITAR_AFASTAMENTO");
    }

    @Test
    void admin_temTodasAsPermissoesDoEnum() {
        assertThat(nomesDe("ADMIN"))
                .containsExactlyInAnyOrderElementsOf(nomesDoEnum());
    }

    @Test
    void liderMocidade_naoTemGestaoDePapeisNemDeUsuarios() {
        assertThat(nomesDe("LIDER_MOCIDADE"))
                .doesNotContain("ROLE_APROVAR", "ROLE_GERENCIAR_PERMISSOES", "USUARIO_GERENCIAR");
    }

    @Test
    void aprovarAfastamento_ficaSoComLiderMocidadeEPastorEAdmin() {
        assertThat(nomesDe("LIDER_MOCIDADE")).contains("CORISTA_APROVAR_AFASTAMENTO");
        assertThat(nomesDe("PASTOR")).contains("CORISTA_APROVAR_AFASTAMENTO");
        assertThat(nomesDe("LIDER_ADOLESCENTES")).doesNotContain("CORISTA_APROVAR_AFASTAMENTO");
        assertThat(nomesDe("PRESBITERO")).doesNotContain("CORISTA_APROVAR_AFASTAMENTO");
    }

    @Test
    void todoNomeSemeadoNoBancoExisteNoEnum() {
        List<String> nomesDoBanco = roleRepository.findAll().stream()
                .flatMap(role -> roleRepository.findNomesDePermissoesPorNomeDaRole(role.getNome()).stream())
                .toList();

        assertThat(nomesDoBanco).isNotEmpty().allMatch(nome -> nomesDoEnum().contains(nome));
    }

    private List<String> nomesDe(String nomeDaRole) {
        return roleRepository.findNomesDePermissoesPorNomeDaRole(nomeDaRole);
    }

    private static List<String> nomesDoEnum() {
        return Arrays.stream(Permissao.values()).map(Enum::name).toList();
    }
}
