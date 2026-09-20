package com.registraai.registro_coristas_api.telefone.repository;

import com.registraai.registro_coristas_api.area.model.Area;
import com.registraai.registro_coristas_api.area.repository.AreaRepository;
import com.registraai.registro_coristas_api.congregacao.model.Congregacao;
import com.registraai.registro_coristas_api.congregacao.repository.CongregacaoRepository;
import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.pessoa.repository.PessoaRepository;
import com.registraai.registro_coristas_api.telefone.model.Telefone;
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

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sobe o schema real via Flyway num Postgres 16 (Testcontainers) com {@code ddl-auto: validate}: se o mapeamento
 * de {@link Telefone} divergir da migration V5, o contexto nem sobe. Também exercita as duas garantias do banco
 * (número único por pessoa e um único principal por pessoa).
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TelefoneRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private TelefoneRepository telefoneRepository;

    @Autowired
    private PessoaRepository pessoaRepository;

    @Autowired
    private CongregacaoRepository congregacaoRepository;

    @Autowired
    private AreaRepository areaRepository;

    private Congregacao congregacao;
    private Pessoa pessoa;

    @BeforeEach
    void criarPessoa() {
        Area area = areaRepository.saveAndFlush(Area.builder().numero(40).nome("Área 40").build());
        congregacao = congregacaoRepository.saveAndFlush(Congregacao.builder().area(area).nome("Sede").build());
        pessoa = novaPessoa("Maria da Silva");
    }

    private Pessoa novaPessoa(String nome) {
        return pessoaRepository.saveAndFlush(Pessoa.builder()
                .nome(nome).dataNascimento(LocalDate.of(1995, 5, 17)).congregacao(congregacao).build());
    }

    private Telefone salvar(Pessoa dona, String numero, boolean principal) {
        return telefoneRepository.saveAndFlush(
                Telefone.builder().pessoa(dona).numero(numero).principal(principal).build());
    }

    @Test
    void salvar_geraIdTimestampsEFlagsFalsePorPadrao() {
        Telefone salvo = salvar(pessoa, "81999990000", false);

        assertThat(salvo.getId()).isNotNull();
        assertThat(salvo.getCriadoEm()).isNotNull();
        assertThat(salvo.getAtualizadoEm()).isNotNull();
        assertThat(salvo.isWhatsapp()).isFalse();
        assertThat(salvo.isPrincipal()).isFalse();
    }

    @Test
    void salvar_mesmoNumeroParaMesmaPessoa_violaUnicidade() {
        salvar(pessoa, "81999990000", false);

        assertThatThrownBy(() -> salvar(pessoa, "81999990000", false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void salvar_mesmoNumeroParaPessoasDiferentes_eAceito() {
        salvar(pessoa, "81999990000", false);

        Telefone deOutra = salvar(novaPessoa("Ana Souza"), "81999990000", false);

        assertThat(deOutra.getId()).isNotNull();
    }

    @Test
    void salvar_doisPrincipaisParaAMesmaPessoa_violaIndiceUnicoParcial() {
        salvar(pessoa, "81999990000", true);

        assertThatThrownBy(() -> salvar(pessoa, "8133334444", true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void salvar_umPrincipalPorPessoa_pessoasDiferentesPodemTerCadaUmOSeu() {
        salvar(pessoa, "81999990000", true);

        Telefone principalDeOutra = salvar(novaPessoa("Ana Souza"), "81988887777", true);

        assertThat(principalDeOutra.isPrincipal()).isTrue();
    }

    @Test
    void salvar_variosNaoPrincipaisParaAMesmaPessoa_saoAceitos() {
        salvar(pessoa, "81999990000", false);
        salvar(pessoa, "8133334444", false);

        assertThat(telefoneRepository.findByPessoaIdOrderByPrincipalDescCriadoEmAsc(pessoa.getId())).hasSize(2);
    }

    @Test
    void findByPessoaIdOrderByPrincipalDescCriadoEmAsc_principalPrimeiroDepoisOsMaisAntigos() throws InterruptedException {
        Telefone maisAntigo = salvar(pessoa, "8133334444", false);
        Thread.sleep(5); // separa os criado_em para a ordem não depender de empate
        Telefone principal = salvar(pessoa, "81999990000", true);
        Thread.sleep(5);
        Telefone maisNovo = salvar(pessoa, "81988887777", false);
        salvar(novaPessoa("Ana Souza"), "81977776666", true);

        assertThat(telefoneRepository.findByPessoaIdOrderByPrincipalDescCriadoEmAsc(pessoa.getId()))
                .extracting(Telefone::getId)
                .containsExactly(principal.getId(), maisAntigo.getId(), maisNovo.getId());
    }

    @Test
    void findByIdAndPessoaId_soEncontraQuandoOTelefoneEDaPessoa() {
        Telefone telefone = salvar(pessoa, "81999990000", false);
        Pessoa outra = novaPessoa("Ana Souza");

        assertThat(telefoneRepository.findByIdAndPessoaId(telefone.getId(), pessoa.getId())).isPresent();
        assertThat(telefoneRepository.findByIdAndPessoaId(telefone.getId(), outra.getId())).isEmpty();
        assertThat(telefoneRepository.findByIdAndPessoaId(UUID.randomUUID(), pessoa.getId())).isEmpty();
    }

    @Test
    void findByPessoaIdAndPrincipalTrue_devolveSoOPrincipalDaPessoa() {
        salvar(pessoa, "8133334444", false);
        Telefone principal = salvar(pessoa, "81999990000", true);
        salvar(novaPessoa("Ana Souza"), "81988887777", true);

        assertThat(telefoneRepository.findByPessoaIdAndPrincipalTrue(pessoa.getId()))
                .get().extracting(Telefone::getId).isEqualTo(principal.getId());
    }

    @Test
    void findByPessoaIdAndPrincipalTrue_semPrincipal_devolveVazio() {
        salvar(pessoa, "8133334444", false);

        assertThat(telefoneRepository.findByPessoaIdAndPrincipalTrue(pessoa.getId())).isEmpty();
    }

    @Test
    void existsByPessoaId_soConsideraAPessoaInformada() {
        salvar(pessoa, "81999990000", false);
        Pessoa semTelefone = novaPessoa("Ana Souza");

        assertThat(telefoneRepository.existsByPessoaId(pessoa.getId())).isTrue();
        assertThat(telefoneRepository.existsByPessoaId(semTelefone.getId())).isFalse();
    }

    @Test
    void existsByPessoaIdAndNumero_comparaNumeroExatoDentroDaPessoa() {
        salvar(pessoa, "81999990000", false);
        Pessoa outra = novaPessoa("Ana Souza");

        assertThat(telefoneRepository.existsByPessoaIdAndNumero(pessoa.getId(), "81999990000")).isTrue();
        assertThat(telefoneRepository.existsByPessoaIdAndNumero(pessoa.getId(), "8133334444")).isFalse();
        assertThat(telefoneRepository.existsByPessoaIdAndNumero(outra.getId(), "81999990000")).isFalse();
    }

    @Test
    void existsByPessoaIdAndNumeroAndIdNot_ignoraOProprioTelefone() {
        Telefone telefone = salvar(pessoa, "81999990000", false);
        Telefone outro = salvar(pessoa, "8133334444", false);

        assertThat(telefoneRepository.existsByPessoaIdAndNumeroAndIdNot(pessoa.getId(), "81999990000", telefone.getId()))
                .isFalse();
        assertThat(telefoneRepository.existsByPessoaIdAndNumeroAndIdNot(pessoa.getId(), "81999990000", outro.getId()))
                .isTrue();
    }
}
