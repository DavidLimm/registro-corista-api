package com.registraai.registro_coristas_api.migracao;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A migração V28 precisa funcionar com dados já existentes: os demais testes começam com banco vazio
 * e não pegariam um backfill errado. Aqui migra até a V27, insere áreas/congregações e migra até a última.
 */
@Testcontainers
class MigracaoHierarquiaTest {

    @Container
    PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    private Flyway flyway(String alvo) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target("latest".equals(alvo) ? MigrationVersion.LATEST : MigrationVersion.fromVersion(alvo))
                .load();
    }

    private String consultaUnica(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    void backfillPreservaCongregacoesESemeia74Areas() throws SQLException {
        flyway("27").migrate();

        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement st = c.createStatement()) {
            st.execute("INSERT INTO area (numero, nome, ativa) VALUES (40, 'Área 40', TRUE), (99, 'Área 99', FALSE)");
            st.execute("INSERT INTO congregacao (area_id, nome) SELECT id, 'Acampamento 2' FROM area WHERE numero = 40");
            st.execute("INSERT INTO congregacao (area_id, nome) SELECT id, 'Sede 99' FROM area WHERE numero = 99");

            flyway("latest").migrate();

            // 74 áreas + a 99 que já existia
            assertThat(consultaUnica(st, "SELECT count(*) FROM agrupamento WHERE tipo = 'AREA'")).isEqualTo("75");
            // cada AREA tem exatamente uma subdivisão implícita (sem número/nome)
            assertThat(consultaUnica(st, "SELECT count(*) FROM subdivisao WHERE numero IS NULL AND nome IS NULL")).isEqualTo("75");
            // a área inativa continua inativa
            assertThat(consultaUnica(st, "SELECT ativo FROM agrupamento WHERE tipo = 'AREA' AND numero = 99")).isEqualTo("f");
            // congregações apontam para a subdivisão da área certa
            assertThat(consultaUnica(st, """
                    SELECT g.numero FROM congregacao c
                    JOIN subdivisao s ON s.id = c.subdivisao_id
                    JOIN agrupamento g ON g.id = s.agrupamento_id
                    WHERE c.nome = 'Acampamento 2'""")).isEqualTo("40");
            assertThat(consultaUnica(st, "SELECT count(*) FROM congregacao WHERE subdivisao_id IS NULL")).isEqualTo("0");
        }
    }

    @Test
    void constraintsPorTipoRecusamDadosInvalidos() throws SQLException {
        flyway("latest").migrate();

        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement st = c.createStatement()) {
            // campo missionário sem país
            assertThatThrownBy(() -> st.execute("INSERT INTO agrupamento (tipo, nome) VALUES ('CAMPO_MISSIONARIO', 'Argentina')"))
                    .isInstanceOf(SQLException.class);
            // área com nome
            assertThatThrownBy(() -> st.execute("INSERT INTO agrupamento (tipo, numero, nome) VALUES ('AREA', 200, 'x')"))
                    .isInstanceOf(SQLException.class);
            // área número 40 duplicada
            assertThatThrownBy(() -> st.execute("INSERT INTO agrupamento (tipo, numero) VALUES ('AREA', 40)"))
                    .isInstanceOf(SQLException.class);
            // mesmo número em setor e em área é permitido
            st.execute("INSERT INTO agrupamento (tipo, numero, nome) VALUES ('SETOR', 40, 'Setor 40')");
        }
    }
}
