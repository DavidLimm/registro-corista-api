package com.registraai.registro_coristas_api.usuario.repository;

import com.registraai.registro_coristas_api.area.model.Area;
import com.registraai.registro_coristas_api.area.repository.AreaRepository;
import com.registraai.registro_coristas_api.congregacao.model.Congregacao;
import com.registraai.registro_coristas_api.congregacao.repository.CongregacaoRepository;
import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.pessoa.model.StatusPessoa;
import com.registraai.registro_coristas_api.pessoa.repository.PessoaRepository;
import com.registraai.registro_coristas_api.role.model.Role;
import com.registraai.registro_coristas_api.role.repository.RoleRepository;
import com.registraai.registro_coristas_api.usuario.model.Usuario;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sobe o schema real via Flyway num Postgres 16 (Testcontainers) com {@code ddl-auto: validate}: se o mapeamento de
 * {@link Usuario} divergir da V11, o contexto nem sobe. Também exercita as constraints únicas de pessoa e e-mail.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UsuarioRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PessoaRepository pessoaRepository;

    @Autowired
    private CongregacaoRepository congregacaoRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private RoleRepository roleRepository;

    private Congregacao congregacao;

    @BeforeEach
    void criarCongregacao() {
        Area area = areaRepository.saveAndFlush(Area.builder().numero(40).nome("Área 40").build());
        congregacao = congregacaoRepository.saveAndFlush(Congregacao.builder().area(area).nome("Sede").build());
    }

    private Pessoa novaPessoaAprovada(String nome) {
        return pessoaRepository.saveAndFlush(Pessoa.builder()
                .nome(nome).dataNascimento(LocalDate.of(1990, 3, 1)).congregacao(congregacao)
                .status(StatusPessoa.APROVADO).build());
    }

    private Role roleExistente(String nome) {
        return roleRepository.findAll().stream().filter(r -> r.getNome().equals(nome)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Role " + nome + " não seedada pela V7"));
    }

    private Usuario.UsuarioBuilder usuarioValido(Pessoa pessoa) {
        return Usuario.builder().pessoa(pessoa).email(pessoa.getNome().toLowerCase() + "@exemplo.com")
                .senhaHash("hash").roles(Set.of(roleExistente("CORISTA_JOVENS")));
    }

    @Test
    void salvar_geraIdETimestampsEPersisteCamposDoUsuario() {
        Pessoa pessoa = novaPessoaAprovada("Ana");

        Usuario salvo = usuarioRepository.saveAndFlush(usuarioValido(pessoa).build());

        assertThat(salvo.getId()).isNotNull();
        assertThat(salvo.getCriadoEm()).isNotNull();
        assertThat(salvo.getAtualizadoEm()).isNotNull();
        Usuario lido = usuarioRepository.findById(salvo.getId()).orElseThrow();
        assertThat(lido.getPessoa().getId()).isEqualTo(pessoa.getId());
        assertThat(lido.getEmail()).isEqualTo("ana@exemplo.com");
        assertThat(lido.isAtivo()).isTrue();
        assertThat(lido.getRoles()).extracting(Role::getNome).containsExactly("CORISTA_JOVENS");
    }

    @Test
    void salvar_duasVezesParaAMesmaPessoaViolaUnicidadeDaEspecializacao() {
        Pessoa pessoa = novaPessoaAprovada("Bia");
        usuarioRepository.saveAndFlush(usuarioValido(pessoa).build());

        assertThatThrownBy(() -> usuarioRepository.saveAndFlush(
                usuarioValido(pessoa).email("outro@exemplo.com").build()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_usuario_pessoa");
    }

    @Test
    void salvar_emailDuplicadoViolaUnicidade() {
        usuarioRepository.saveAndFlush(usuarioValido(novaPessoaAprovada("Carla")).email("mesmo@exemplo.com").build());

        assertThatThrownBy(() -> usuarioRepository.saveAndFlush(
                usuarioValido(novaPessoaAprovada("Duda")).email("mesmo@exemplo.com").build()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_usuario_email");
    }

    @Test
    void salvar_semPessoaViolaNotNullDoBanco() {
        assertThatThrownBy(() -> usuarioRepository.saveAndFlush(
                Usuario.builder().email("a@a.com").senhaHash("hash").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void salvar_semRolesEPossivel() {
        // roles ficam numa tabela de junção à parte; um usuário sem nenhum papel ainda é persistível no banco
        // (a regra de "pelo menos um role" é do Service/DTO, não do schema)
        Usuario salvo = usuarioRepository.saveAndFlush(
                Usuario.builder().pessoa(novaPessoaAprovada("Eva")).email("eva@exemplo.com").senhaHash("hash").build());

        assertThat(usuarioRepository.findById(salvo.getId()).orElseThrow().getRoles()).isEmpty();
    }
}
