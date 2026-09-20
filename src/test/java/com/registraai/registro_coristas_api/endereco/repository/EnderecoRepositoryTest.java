package com.registraai.registro_coristas_api.endereco.repository;

import com.registraai.registro_coristas_api.endereco.model.Endereco;
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
 * Sobe o schema real via Flyway num Postgres 16 (Testcontainers) com {@code ddl-auto: validate}:
 * se o mapeamento de {@link Endereco} divergir da migration V1, o contexto nem sobe.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EnderecoRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private EnderecoRepository enderecoRepository;

    private Endereco.EnderecoBuilder enderecoValido() {
        return Endereco.builder()
                .logradouro("Rua da Aurora").numero("123").complemento("Apto 4").bairro("Boa Vista")
                .cidade("Recife").uf("PE").cep("50050000");
    }

    @Test
    void salvar_geraIdETimestamps() {
        Endereco salvo = enderecoRepository.saveAndFlush(enderecoValido().build());

        assertThat(salvo.getId()).isNotNull();
        assertThat(salvo.getCriadoEm()).isNotNull();
        assertThat(salvo.getAtualizadoEm()).isNotNull();
        assertThat(enderecoRepository.findById(salvo.getId())).isPresent();
    }

    @Test
    void salvar_aceitaCepENumeroNulos() {
        Endereco salvo = enderecoRepository.saveAndFlush(enderecoValido().cep(null).numero(null).complemento(null).build());

        assertThat(salvo.getId()).isNotNull();
    }

    @Test
    void salvar_cepForaDoPadraoViolaConstraintDoBanco() {
        // 8 caracteres (cabe no VARCHAR(8)) mas não numérico: exercita a CHECK ck_endereco_cep
        assertThatThrownBy(() -> enderecoRepository.saveAndFlush(enderecoValido().cep("5005a000").build()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_endereco_cep");
    }

    @Test
    void salvar_ufMinusculaViolaConstraintDoBanco() {
        assertThatThrownBy(() -> enderecoRepository.saveAndFlush(enderecoValido().uf("pe").build()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_endereco_uf");
    }
}
