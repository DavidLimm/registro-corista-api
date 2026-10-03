package com.registraai.registro_coristas_api.usuario;

import com.registraai.registro_coristas_api.pessoa.service.PessoaService;
import com.registraai.registro_coristas_api.role.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fluxo completo Controller → Service → Repository → Postgres real (Flyway aplica o schema, incluindo a V11). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UsuarioIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    private static final ZoneId RECIFE = ZoneId.of("America/Recife");

    @Autowired
    private MockMvc mockMvc;

    // aprovação de pessoa ainda não tem endpoint HTTP (depende de autenticação, ver AGENTS.md) — o teste de
    // integração do próprio Corista/Pessoa cobre o workflow PENDENTE/APROVADO; aqui só precisamos do estado final
    @Autowired
    private PessoaService pessoaService;

    @Autowired
    private RoleRepository roleRepository;

    private String idDoLocation(String location) {
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private String criarArea(int numero) throws Exception {
        String location = mockMvc.perform(post("/v1/api/areas").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"numero\": %d, \"nome\": \"Área %d\" }".formatted(numero, numero)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return idDoLocation(location);
    }

    private String criarCongregacao(String areaId, String nome) throws Exception {
        String location = mockMvc.perform(post("/v1/api/congregacoes").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"areaId\": \"%s\", \"nome\": \"%s\" }".formatted(areaId, nome)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return idDoLocation(location);
    }

    /** Cadastra uma pessoa adulta (sem exigências de LGPD) e já aprova o cadastro. */
    private String criarPessoaAprovada(String congregacaoId, String nome) throws Exception {
        LocalDate adulto = LocalDate.now(RECIFE).minusYears(30);
        String location = mockMvc.perform(post("/v1/api/pessoas").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nome\": \"%s\", \"dataNascimento\": \"%s\", \"congregacaoId\": \"%s\" }"
                                .formatted(nome, adulto, congregacaoId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        String pessoaId = idDoLocation(location);
        pessoaService.aprovar(UUID.fromString(pessoaId), UUID.randomUUID());
        return pessoaId;
    }

    private UUID idDaRole(String nome) {
        return roleRepository.findAll().stream().filter(r -> r.getNome().equals(nome)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Role " + nome + " não seedada pela V7"))
                .getId();
    }

    private String corpo(String pessoaId, String email, String senha, String roleIdsJson) {
        return """
                { "pessoaId": "%s", "email": "%s", "senha": "%s", "roleIds": %s }"""
                .formatted(pessoaId, email, senha, roleIdsJson);
    }

    // ---------- cadastro ----------

    @Test
    void criar_sucesso_resolveNomeDaPessoaENomesDosRoles() throws Exception {
        String congregacaoId = criarCongregacao(criarArea(20), "Sede");
        String pessoaId = criarPessoaAprovada(congregacaoId, "Maria da Silva");
        UUID roleId = idDaRole("CORISTA_JOVENS");

        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(pessoaId, "maria@exemplo.com", "senhaForte123", "[\"" + roleId + "\"]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pessoaId").value(pessoaId))
                .andExpect(jsonPath("$.pessoaNome").value("Maria da Silva"))
                .andExpect(jsonPath("$.email").value("maria@exemplo.com"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.roles[0]").value("CORISTA_JOVENS"))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
    }

    @Test
    void criar_normalizaEmailParaMinusculas() throws Exception {
        String congregacaoId = criarCongregacao(criarArea(21), "Sede");
        String pessoaId = criarPessoaAprovada(congregacaoId, "Bia");
        UUID roleId = idDaRole("CORISTA_JOVENS");

        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(pessoaId, "Bia@Exemplo.COM", "senhaForte123", "[\"" + roleId + "\"]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("bia@exemplo.com"));
    }

    @Test
    void criar_pessoaPendente_epermitido_naoExigeAprovacao() throws Exception {
        // auto-cadastro cria pessoa e usuário juntos, ainda PENDENTE (ver CoristaIntegracaoTest)
        String congregacaoId = criarCongregacao(criarArea(22), "Sede");
        String location = mockMvc.perform(post("/v1/api/pessoas").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nome\": \"Caio\", \"dataNascimento\": \"%s\", \"congregacaoId\": \"%s\" }"
                                .formatted(LocalDate.now(RECIFE).minusYears(30), congregacaoId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        String pessoaId = idDoLocation(location); // sem aprovar

        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(pessoaId, "caio@exemplo.com", "senhaForte123", "[\"" + idDaRole("CORISTA_JOVENS") + "\"]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("caio@exemplo.com"));
    }

    @Test
    void criar_pessoaJaTemUsuario_retorna409() throws Exception {
        String congregacaoId = criarCongregacao(criarArea(23), "Sede");
        String pessoaId = criarPessoaAprovada(congregacaoId, "Duda");
        UUID roleId = idDaRole("CORISTA_JOVENS");
        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(pessoaId, "duda1@exemplo.com", "senhaForte123", "[\"" + roleId + "\"]")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(pessoaId, "duda2@exemplo.com", "senhaForte123", "[\"" + roleId + "\"]")))
                .andExpect(status().isConflict());
    }

    @Test
    void criar_emailDuplicado_retorna409() throws Exception {
        String congregacaoId = criarCongregacao(criarArea(24), "Sede");
        UUID roleId = idDaRole("CORISTA_JOVENS");
        String pessoa1 = criarPessoaAprovada(congregacaoId, "Elis");
        String pessoa2 = criarPessoaAprovada(congregacaoId, "Fabi");
        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(pessoa1, "mesmo@exemplo.com", "senhaForte123", "[\"" + roleId + "\"]")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(pessoa2, "mesmo@exemplo.com", "senhaForte123", "[\"" + roleId + "\"]")))
                .andExpect(status().isConflict());
    }

    @Test
    void criar_roleInexistente_retorna404EInativa_retorna409() throws Exception {
        String congregacaoId = criarCongregacao(criarArea(25), "Sede");
        String pessoaId = criarPessoaAprovada(congregacaoId, "Gabi");

        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(pessoaId, "gabi@exemplo.com", "senhaForte123",
                                "[\"00000000-0000-0000-0000-000000000000\"]")))
                .andExpect(status().isNotFound());

        UUID roleId = idDaRole("APOIO_JOVENS");
        mockMvc.perform(delete("/v1/api/roles/" + roleId)).andExpect(status().isNoContent());

        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(pessoaId, "gabi2@exemplo.com", "senhaForte123", "[\"" + roleId + "\"]")))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/v1/api/roles/" + roleId + "/reativar")).andExpect(status().isOk());
    }

    @Test
    void criar_roleIdsComElementoNulo_retorna400() throws Exception {
        String congregacaoId = criarCongregacao(criarArea(26), "Sede");
        String pessoaId = criarPessoaAprovada(congregacaoId, "Helo");

        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(pessoaId, "helo@exemplo.com", "senhaForte123",
                                "[\"" + idDaRole("CORISTA_JOVENS") + "\", null]")))
                .andExpect(status().isBadRequest());
    }

    // ---------- edição ----------

    @Test
    void atualizar_trocaEmailERoles() throws Exception {
        String congregacaoId = criarCongregacao(criarArea(27), "Sede");
        String pessoaId = criarPessoaAprovada(congregacaoId, "Ivo");
        UUID corista = idDaRole("CORISTA_JOVENS");
        UUID maestro = idDaRole("MAESTRO_JOVENS");
        String location = mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(pessoaId, "ivo@exemplo.com", "senhaForte123", "[\"" + corista + "\"]")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(pessoaId, "ivo.novo@exemplo.com", "outraSenha123", "[\"" + maestro + "\"]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ivo.novo@exemplo.com"))
                .andExpect(jsonPath("$.roles[0]").value("MAESTRO_JOVENS"));
    }

    @Test
    void atualizar_pessoaDiferente_retorna409() throws Exception {
        String congregacaoId = criarCongregacao(criarArea(28), "Sede");
        String pessoaId = criarPessoaAprovada(congregacaoId, "Julia");
        String outraPessoaId = criarPessoaAprovada(congregacaoId, "Karla");
        UUID roleId = idDaRole("CORISTA_JOVENS");
        String location = mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(pessoaId, "julia@exemplo.com", "senhaForte123", "[\"" + roleId + "\"]")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(outraPessoaId, "julia@exemplo.com", "senhaForte123", "[\"" + roleId + "\"]")))
                .andExpect(status().isConflict());
    }

    // ---------- inativação / reativação ----------

    @Test
    void inativarEReativar_fazSoftDeleteMantendoOCadastroConsultavel() throws Exception {
        String congregacaoId = criarCongregacao(criarArea(29), "Sede");
        String pessoaId = criarPessoaAprovada(congregacaoId, "Léo");
        UUID roleId = idDaRole("CORISTA_JOVENS");
        String location = mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(pessoaId, "leo@exemplo.com", "senhaForte123", "[\"" + roleId + "\"]")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(delete(location)).andExpect(status().isNoContent());
        mockMvc.perform(get(location)).andExpect(jsonPath("$.ativo").value(false));

        mockMvc.perform(patch(location + "/reativar")).andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));
    }

    // ---------- listagem ----------

    @Test
    void listar_paginaEFiltraPorAtivo() throws Exception {
        String congregacaoId = criarCongregacao(criarArea(30), "Sede");
        UUID roleId = idDaRole("CORISTA_JOVENS");
        String ativoLocation = mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(criarPessoaAprovada(congregacaoId, "Marco"), "marco@exemplo.com",
                                "senhaForte123", "[\"" + roleId + "\"]")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        String inativoLocation = mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(criarPessoaAprovada(congregacaoId, "Nina"), "nina@exemplo.com",
                                "senhaForte123", "[\"" + roleId + "\"]")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        mockMvc.perform(delete(inativoLocation)).andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/api/usuarios").param("ativo", "true").param("size", "50"))
                .andExpect(jsonPath("$.content[?(@.email == 'marco@exemplo.com')]").exists())
                .andExpect(jsonPath("$.content[?(@.email == 'nina@exemplo.com')]").doesNotExist());

        mockMvc.perform(get("/v1/api/usuarios").param("ativo", "false").param("size", "50"))
                .andExpect(jsonPath("$.content[?(@.email == 'nina@exemplo.com')]").exists());
    }

    @Test
    void buscarInexistente_retorna404() throws Exception {
        mockMvc.perform(get("/v1/api/usuarios/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
