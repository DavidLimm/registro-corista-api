package com.registraai.registro_coristas_api.pessoa.repository;

import com.registraai.registro_coristas_api.area.model.Area;
import com.registraai.registro_coristas_api.area.repository.AreaRepository;
import com.registraai.registro_coristas_api.congregacao.model.Congregacao;
import com.registraai.registro_coristas_api.congregacao.repository.CongregacaoRepository;
import com.registraai.registro_coristas_api.endereco.model.Endereco;
import com.registraai.registro_coristas_api.endereco.repository.EnderecoRepository;
import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.pessoa.model.StatusPessoa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sobe o schema real via Flyway num Postgres 16 (Testcontainers) com {@code ddl-auto: validate}:
 * se o mapeamento de {@link Pessoa} divergir da migration V4, o contexto nem sobe.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PessoaRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private PessoaRepository pessoaRepository;

    @Autowired
    private CongregacaoRepository congregacaoRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private EnderecoRepository enderecoRepository;

    private Congregacao congregacao;

    @BeforeEach
    void criarCongregacao() {
        Area area = areaRepository.saveAndFlush(Area.builder().numero(40).nome("Área 40").build());
        congregacao = congregacaoRepository.saveAndFlush(Congregacao.builder().area(area).nome("Sede").build());
    }

    private Pessoa.PessoaBuilder pessoaValida() {
        return Pessoa.builder()
                .nome("Maria da Silva")
                .dataNascimento(LocalDate.of(2000, 5, 17))
                .congregacao(congregacao);
    }

    @Test
    void salvar_geraIdTimestampsEStatusPendentePorPadrao() {
        Pessoa salva = pessoaRepository.saveAndFlush(pessoaValida().build());

        assertThat(salva.getId()).isNotNull();
        assertThat(salva.getCriadoEm()).isNotNull();
        assertThat(salva.getAtualizadoEm()).isNotNull();
        assertThat(salva.getStatus()).isEqualTo(StatusPessoa.PENDENTE);
        assertThat(salva.getEndereco()).isNull();
    }

    @Test
    void salvar_persisteTodosOsCamposIncluindoEnderecoResponsavelEAprovacao() {
        Endereco endereco = enderecoRepository.saveAndFlush(Endereco.builder()
                .logradouro("Rua da Aurora").bairro("Boa Vista").cidade("Recife").uf("PE").build());
        UUID aprovador = UUID.randomUUID();
        Instant agora = Instant.parse("2026-09-20T12:00:00Z");

        Pessoa salva = pessoaRepository.saveAndFlush(pessoaValida()
                .status(StatusPessoa.APROVADO)
                .endereco(endereco)
                .responsavelLegalNome("José da Silva")
                .responsavelLegalTelefone("81999990000")
                .consentimentoLgpdEm(agora)
                .aprovadoPor(aprovador)
                .aprovadoEm(agora)
                .build());

        Pessoa lida = pessoaRepository.findById(salva.getId()).orElseThrow();
        assertThat(lida.getStatus()).isEqualTo(StatusPessoa.APROVADO);
        assertThat(lida.getDataNascimento()).isEqualTo(LocalDate.of(2000, 5, 17));
        assertThat(lida.getEndereco().getId()).isEqualTo(endereco.getId());
        assertThat(lida.getResponsavelLegalNome()).isEqualTo("José da Silva");
        assertThat(lida.getConsentimentoLgpdEm()).isEqualTo(agora);
        assertThat(lida.getAprovadoPor()).isEqualTo(aprovador);
    }

    @Test
    void salvar_responsavelLegalIncompletoViolaConstraintDoBanco() {
        assertThatThrownBy(() -> pessoaRepository.saveAndFlush(
                pessoaValida().responsavelLegalNome("José da Silva").build()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_pessoa_responsavel_legal_completo");
    }

    @Test
    void salvar_aprovacaoIncompletaViolaConstraintDoBanco() {
        assertThatThrownBy(() -> pessoaRepository.saveAndFlush(
                pessoaValida().aprovadoPor(UUID.randomUUID()).build()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_pessoa_aprovacao_completa");
    }
}
