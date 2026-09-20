package com.registraai.registro_coristas_api.corista.repository;

import com.registraai.registro_coristas_api.area.model.Area;
import com.registraai.registro_coristas_api.area.repository.AreaRepository;
import com.registraai.registro_coristas_api.congregacao.model.Congregacao;
import com.registraai.registro_coristas_api.congregacao.repository.CongregacaoRepository;
import com.registraai.registro_coristas_api.corista.model.Corista;
import com.registraai.registro_coristas_api.corista.model.ListaClassificacao;
import com.registraai.registro_coristas_api.corista.model.TamanhoCamisa;
import com.registraai.registro_coristas_api.corista.model.TipoVoz;
import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.pessoa.repository.PessoaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * se o mapeamento de {@link Corista} divergir das migrations V6/V8, o contexto nem sobe. Também exercita as CHECKs
 * que protegem as regras de classificação e promoção e os valores permitidos de voz e camisa.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CoristaRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private CoristaRepository coristaRepository;

    @Autowired
    private PessoaRepository pessoaRepository;

    @Autowired
    private CongregacaoRepository congregacaoRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Congregacao congregacao;

    @BeforeEach
    void criarCongregacao() {
        Area area = areaRepository.saveAndFlush(Area.builder().numero(40).nome("Área 40").build());
        congregacao = congregacaoRepository.saveAndFlush(Congregacao.builder().area(area).nome("Sede").build());
    }

    private Pessoa novaPessoa(String nome) {
        return pessoaRepository.saveAndFlush(Pessoa.builder()
                .nome(nome).dataNascimento(LocalDate.of(2010, 3, 1)).congregacao(congregacao).build());
    }

    private Corista.CoristaBuilder coristaValido(Pessoa pessoa) {
        return Corista.builder().pessoa(pessoa)
                .tipoVoz(TipoVoz.SOPRANO).tamanhoCamisa(TamanhoCamisa.M)
                .listaClassificacao(ListaClassificacao.ADOLESCENTE);
    }

    @Test
    void salvar_geraIdETimestampsEPersisteCamposExclusivosDeCorista() {
        Pessoa pessoa = novaPessoa("Ana");

        Corista salvo = coristaRepository.saveAndFlush(coristaValido(pessoa)
                .tipoVoz(TipoVoz.TENOR).tamanhoCamisa(TamanhoCamisa.XGG).ocupacao("Estudante").build());

        assertThat(salvo.getId()).isNotNull();
        assertThat(salvo.getCriadoEm()).isNotNull();
        assertThat(salvo.getAtualizadoEm()).isNotNull();
        Corista lido = coristaRepository.findById(salvo.getId()).orElseThrow();
        assertThat(lido.getPessoa().getId()).isEqualTo(pessoa.getId());
        assertThat(lido.getTipoVoz()).isEqualTo(TipoVoz.TENOR);
        assertThat(lido.getTamanhoCamisa()).isEqualTo(TamanhoCamisa.XGG);
        assertThat(lido.getOcupacao()).isEqualTo("Estudante");
        assertThat(lido.getListaClassificacao()).isEqualTo(ListaClassificacao.ADOLESCENTE);
        assertThat(lido.getPromovidoPor()).isNull();
        assertThat(lido.getPromovidoEm()).isNull();
    }

    @Test
    void salvar_soAOcupacaoEOpcional() {
        Corista salvo = coristaRepository.saveAndFlush(coristaValido(novaPessoa("Bia")).build());

        assertThat(salvo.getOcupacao()).isNull();
        assertThat(salvo.getTipoVoz()).isNotNull();
        assertThat(salvo.getTamanhoCamisa()).isNotNull();
    }

    @Test
    void salvar_semTipoVozViolaNotNullDoBanco() {
        assertThatThrownBy(() -> coristaRepository.saveAndFlush(coristaValido(novaPessoa("Bia")).tipoVoz(null).build()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("tipo_voz");
    }

    @Test
    void salvar_semTamanhoDeCamisaViolaNotNullDoBanco() {
        assertThatThrownBy(() -> coristaRepository.saveAndFlush(coristaValido(novaPessoa("Bia")).tamanhoCamisa(null).build()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("tamanho_camisa");
    }

    @Test
    void salvar_todosOsValoresDosEnumsSaoAceitosPelasChecksDoBanco() {
        // guarda contra divergência: se alguém acrescentar um valor ao enum e esquecer a CHECK (ou o contrário), quebra aqui
        int rodadas = Math.max(TipoVoz.values().length, TamanhoCamisa.values().length);
        for (int i = 0; i < rodadas; i++) {
            TipoVoz voz = TipoVoz.values()[i % TipoVoz.values().length];
            TamanhoCamisa camisa = TamanhoCamisa.values()[i % TamanhoCamisa.values().length];

            Corista salvo = coristaRepository.saveAndFlush(
                    coristaValido(novaPessoa("Corista " + i)).tipoVoz(voz).tamanhoCamisa(camisa).build());

            assertThat(coristaRepository.findById(salvo.getId()).orElseThrow().getTipoVoz()).isEqualTo(voz);
            assertThat(coristaRepository.findById(salvo.getId()).orElseThrow().getTamanhoCamisa()).isEqualTo(camisa);
        }
    }

    // O enum Java impede valor inválido; só SQL direto chega nas CHECKs da V8. Um teste por violação: depois do
    // primeiro erro o Postgres aborta a transação (25P02) e recusa os comandos seguintes.

    @Test
    void tipoVozForaDoEnumViolaCheckDoBanco() {
        Corista salvo = coristaRepository.saveAndFlush(coristaValido(novaPessoa("Fabi")).build());

        assertThatThrownBy(() -> jdbcTemplate.update("update corista set tipo_voz = 'FALSETE' where id = ?", salvo.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_corista_tipo_voz");
    }

    @Test
    void tamanhoDeCamisaForaDoEnumViolaCheckDoBanco() {
        Corista salvo = coristaRepository.saveAndFlush(coristaValido(novaPessoa("Gabi")).build());

        assertThatThrownBy(() -> jdbcTemplate.update("update corista set tamanho_camisa = 'XXL' where id = ?", salvo.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_corista_tamanho_camisa");
    }

    @Test
    void valorEmMinusculasViolaCheckDoBanco() {
        // o que o Hibernate grava são os nomes exatos dos enums
        Corista salvo = coristaRepository.saveAndFlush(coristaValido(novaPessoa("Helena")).build());

        assertThatThrownBy(() -> jdbcTemplate.update("update corista set tipo_voz = 'soprano' where id = ?", salvo.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_corista_tipo_voz");
    }

    @Test
    void salvar_duasVezesParaAMesmaPessoaViolaUnicidadeDaEspecializacao() {
        Pessoa pessoa = novaPessoa("Carla");
        coristaRepository.saveAndFlush(coristaValido(pessoa).build());

        assertThatThrownBy(() -> coristaRepository.saveAndFlush(coristaValido(pessoa).build()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_corista_pessoa");
    }

    @Test
    void salvar_semListaDeClassificacaoViolaNotNull() {
        assertThatThrownBy(() -> coristaRepository.saveAndFlush(
                coristaValido(novaPessoa("Duda")).listaClassificacao(null).build()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("lista_classificacao");
    }

    @Test
    void salvar_promocaoAntecipadaCompletaParaJovemEPermitida() {
        UUID lider = UUID.randomUUID();
        Instant agora = Instant.parse("2026-09-20T12:00:00Z");

        Corista salvo = coristaRepository.saveAndFlush(coristaValido(novaPessoa("Eva"))
                .listaClassificacao(ListaClassificacao.JOVEM).promovidoPor(lider).promovidoEm(agora).build());

        Corista lido = coristaRepository.findById(salvo.getId()).orElseThrow();
        assertThat(lido.getListaClassificacao()).isEqualTo(ListaClassificacao.JOVEM);
        assertThat(lido.getPromovidoPor()).isEqualTo(lider);
        assertThat(lido.getPromovidoEm()).isEqualTo(agora);
    }

    @Test
    void salvar_promocaoSemTrilhaCompletaViolaConstraintDoBanco() {
        assertThatThrownBy(() -> coristaRepository.saveAndFlush(coristaValido(novaPessoa("Fabi"))
                .listaClassificacao(ListaClassificacao.JOVEM).promovidoPor(UUID.randomUUID()).build()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_corista_promocao_completa");
    }

    @Test
    void salvar_promocaoMarcadaEmListaDeAdolescenteViolaConstraintDoBanco() {
        assertThatThrownBy(() -> coristaRepository.saveAndFlush(coristaValido(novaPessoa("Gabi"))
                .listaClassificacao(ListaClassificacao.ADOLESCENTE)
                .promovidoPor(UUID.randomUUID()).promovidoEm(Instant.now()).build()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_corista_promocao_somente_para_jovem");
    }
}
