package com.registraai.registro_coristas_api.usuario;

import com.jayway.jsonpath.JsonPath;
import com.registraai.registro_coristas_api.pessoa.service.PessoaService;
import com.registraai.registro_coristas_api.role.repository.RoleRepository;
import com.registraai.registro_coristas_api.suporte.HierarquiaTeste;
import com.registraai.registro_coristas_api.suporte.UsuarioTesteFactory;
import com.registraai.registro_coristas_api.suporte.WithUsuarioTeste;
import com.registraai.registro_coristas_api.usuario.model.Usuario;
import com.registraai.registro_coristas_api.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Escopo por agrupamento e papel PASTOR_ADMIN contra o Postgres real (Flyway aplica a V31). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@WithUsuarioTeste
class EscopoIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    private static final ZoneId RECIFE = ZoneId.of("America/Recife");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PessoaService pessoaService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private String idDoLocation(String location) {
        return HierarquiaTeste.idDoLocation(location);
    }

    /** Cria uma filial com duas subdivisões e uma congregação na primeira; devolve {filialId, congregacaoId}. */
    private String[] filialComCongregacao() throws Exception {
        String filial = HierarquiaTeste.criarFilial(mockMvc);
        String subdivisaoId = HierarquiaTeste.criarSubdivisaoEm(mockMvc, filial, 1);
        String congregacaoId = idDoLocation(mockMvc.perform(post("/v1/api/congregacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"subdivisaoId\": \"%s\", \"nome\": \"Sede\" }".formatted(subdivisaoId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location"));
        return new String[]{idDoLocation(filial), congregacaoId};
    }

    private String criarPessoaAprovada(String congregacaoId, String nome) throws Exception {
        String location = mockMvc.perform(post("/v1/api/pessoas").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nome\": \"%s\", \"dataNascimento\": \"%s\", \"congregacaoId\": \"%s\" }"
                                .formatted(nome, LocalDate.now(RECIFE).minusYears(30), congregacaoId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        String pessoaId = idDoLocation(location);
        pessoaService.aprovar(UUID.fromString(pessoaId), UUID.randomUUID());
        return pessoaId;
    }

    private UUID idDaRole(String nome) {
        return roleRepository.findAll().stream().filter(r -> r.getNome().equals(nome)).findFirst().orElseThrow().getId();
    }

    /** Cria o login com o papel e devolve o id do usuário. */
    private String criarUsuario(String congregacaoId, String nome, String email, String papel) throws Exception {
        return criarUsuario(congregacaoId, nome, email, papel, request -> request);
    }

    private String criarUsuario(String congregacaoId, String nome, String email, String papel, RequestPostProcessor autor)
            throws Exception {
        String pessoaId = criarPessoaAprovada(congregacaoId, nome);
        String location = mockMvc.perform(post("/v1/api/usuarios").with(autor).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "pessoaId": "%s", "email": "%s", "senha": "SenhaForte@123", "roleIds": ["%s"] }"""
                                .formatted(pessoaId, email, idDaRole(papel))))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        return idDoLocation(location);
    }

    private String corpoEscopo(String... agrupamentoIds) {
        return "{ \"agrupamentoIds\": [" + String.join(",", java.util.Arrays.stream(agrupamentoIds)
                .map(id -> "\"" + id + "\"").toList()) + "] }";
    }

    private RequestPostProcessor logadoComo(String papel, String... permissoes) {
        return SecurityMockMvcRequestPostProcessors.securityContext(
                UsuarioTesteFactory.contexto(new String[]{papel}, false, permissoes));
    }

    // ---------- derivação e substituição do escopo ----------

    @Test
    void usuarioCriadoPeloAdmin_herdaOAgrupamentoDaCongregacaoDaPessoa() throws Exception {
        String[] filialECongregacao = filialComCongregacao();
        String usuarioId = criarUsuario(filialECongregacao[1], "Líder Um", "lider1@exemplo.com", "LIDER_MOCIDADE");

        mockMvc.perform(get("/v1/api/usuarios/" + usuarioId + "/agrupamentos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.global").value(false))
                .andExpect(jsonPath("$.agrupamentos", hasSize(1)))
                .andExpect(jsonPath("$.agrupamentos[0].id").value(filialECongregacao[0]));
    }

    @Test
    void autoCadastroDeCorista_nasceComOAgrupamentoDaCongregacaoEscolhida() throws Exception {
        String[] filialECongregacao = filialComCongregacao();

        mockMvc.perform(post("/v1/api/coristas").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "pessoa": { "nome": "Nova Corista", "dataNascimento": "%s", "congregacaoId": "%s" },
                                  "tipoVoz": "SOPRANO", "tamanhoCamisa": "M",
                                  "usuario": { "email": "nova.corista@exemplo.com", "senha": "SenhaForte@123" } }"""
                                .formatted(LocalDate.now(RECIFE).minusYears(25), filialECongregacao[1])))
                .andExpect(status().isCreated());

        Usuario corista = usuarioRepository.findByEmail("nova.corista@exemplo.com").orElseThrow();
        mockMvc.perform(get("/v1/api/usuarios/" + corista.getId() + "/agrupamentos"))
                .andExpect(jsonPath("$.global").value(false))
                .andExpect(jsonPath("$.agrupamentos", hasSize(1)))
                .andExpect(jsonPath("$.agrupamentos[0].id").value(filialECongregacao[0]));
    }

    @Test
    void definirEscopo_substituiOAgrupamentoDoLiderEExigeExatamenteUm() throws Exception {
        String[] a = filialComCongregacao();
        String[] b = filialComCongregacao();
        String usuarioId = criarUsuario(a[1], "Líder Dois", "lider2@exemplo.com", "LIDER_JOVENS");
        String url = "/v1/api/usuarios/" + usuarioId + "/agrupamentos";

        mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(corpoEscopo(b[0])))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agrupamentos", hasSize(1)))
                .andExpect(jsonPath("$.agrupamentos[0].id").value(b[0]));
        mockMvc.perform(get(url)).andExpect(jsonPath("$.agrupamentos[0].id").value(b[0]));

        mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(corpoEscopo(a[0], b[0])))
                .andExpect(status().isConflict());
        mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(corpoEscopo()))
                .andExpect(status().isConflict());
        // continua com o último válido
        mockMvc.perform(get(url)).andExpect(jsonPath("$.agrupamentos[0].id").value(b[0]));
    }

    // ---------- pastor ----------

    @Test
    void pastor_atedoisAgrupamentos_campoMissionarioSozinho_ePodeFicarSemVinculos() throws Exception {
        String[] a = filialComCongregacao();
        String[] b = filialComCongregacao();
        String[] c = filialComCongregacao();
        String campo = idDoLocation(mockMvc.perform(post("/v1/api/agrupamentos").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"tipo\": \"CAMPO_MISSIONARIO\", \"nome\": \"Paraguai\", \"pais\": \"Paraguai\" }"))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location"));
        String pastorId = criarUsuario(a[1], "Pastor Um", "pastor1@exemplo.com", "PASTOR");
        String url = "/v1/api/usuarios/" + pastorId + "/agrupamentos";

        // pastor não ganha vínculo automático: precisa ser designado
        mockMvc.perform(get(url)).andExpect(jsonPath("$.agrupamentos", hasSize(0)));

        mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(corpoEscopo(a[0], b[0])))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agrupamentos", hasSize(2)));
        mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(corpoEscopo(a[0], b[0], c[0])))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("no máximo 2")));
        mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(corpoEscopo(campo, a[0])))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("Campo Missionário")));
        mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(corpoEscopo(campo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agrupamentos[0].tipo").value("CAMPO_MISSIONARIO"));
        mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(corpoEscopo()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agrupamentos", hasSize(0)));
    }

    // ---------- global ----------

    @Test
    void adminEPastorAdmin_saoGlobaisENaoRecebemVinculos() throws Exception {
        String[] a = filialComCongregacao();
        String adminId = criarUsuario(a[1], "Admin Dois", "admin2@exemplo.com", "ADMIN");
        String pastorAdminId = criarUsuario(a[1], "Pastor Geral", "pastor.geral@exemplo.com", "PASTOR_ADMIN");

        for (String id : new String[]{adminId, pastorAdminId}) {
            mockMvc.perform(get("/v1/api/usuarios/" + id + "/agrupamentos"))
                    .andExpect(jsonPath("$.global").value(true))
                    .andExpect(jsonPath("$.agrupamentos", hasSize(0)));
            mockMvc.perform(put("/v1/api/usuarios/" + id + "/agrupamentos").contentType(MediaType.APPLICATION_JSON)
                            .content(corpoEscopo(a[0])))
                    .andExpect(status().isConflict());
        }
    }

    // ---------- PASTOR_ADMIN ----------

    @Test
    void pastorAdmin_temSoLeituraNosDadosEGerenciaEstruturaEEscopo() throws Exception {
        UUID id = idDaRole("PASTOR_ADMIN");
        mockMvc.perform(get("/v1/api/roles/" + id + "/permissoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissoes", hasItem("PESSOA_LER")))
                .andExpect(jsonPath("$.permissoes", hasItem("CORISTA_LER")))
                .andExpect(jsonPath("$.permissoes", hasItem("AGRUPAMENTO_GERENCIAR")))
                .andExpect(jsonPath("$.permissoes", hasItem("ESCOPO_GERENCIAR")))
                .andExpect(jsonPath("$.permissoes", not(hasItem("PESSOA_ATUALIZAR"))))
                .andExpect(jsonPath("$.permissoes", not(hasItem("PESSOA_APROVAR"))))
                .andExpect(jsonPath("$.permissoes", not(hasItem("CORISTA_ATUALIZAR"))))
                .andExpect(jsonPath("$.permissoes", not(hasItem("CORISTA_APROVAR"))))
                .andExpect(jsonPath("$.permissoes", not(hasItem("CORISTA_APROVAR_AFASTAMENTO"))))
                .andExpect(jsonPath("$.permissoes", not(hasItem("CORISTA_PROMOVER"))))
                .andExpect(jsonPath("$.permissoes", not(hasItem("ROLE_GERENCIAR_PERMISSOES"))));
    }

    @Test
    void pastorAdmin_naoTemPermissoesEditaveisPelaApi() throws Exception {
        UUID id = idDaRole("PASTOR_ADMIN");

        mockMvc.perform(put("/v1/api/roles/" + id + "/permissoes").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"permissoes\": [\"PESSOA_ATUALIZAR\"] }"))
                .andExpect(status().isConflict());
    }

    @Test
    void pastorAdmin_soCriaUsuariosComPapelPastor_eNaoMexeEmAdmin() throws Exception {
        String[] a = filialComCongregacao();
        RequestPostProcessor pastorAdmin = logadoComo("PASTOR_ADMIN", "USUARIO_GERENCIAR", "ESCOPO_GERENCIAR");
        String adminId = criarUsuario(a[1], "Admin Três", "admin3@exemplo.com", "ADMIN");

        // pode criar PASTOR
        criarUsuario(a[1], "Pastor Dois", "pastor2@exemplo.com", "PASTOR", pastorAdmin);

        // não pode criar líder, ADMIN nem PASTOR_ADMIN
        for (String papel : List.of("LIDER_MOCIDADE", "ADMIN", "PASTOR_ADMIN")) {
            String pessoaId = criarPessoaAprovada(a[1], "Pessoa " + papel);
            mockMvc.perform(post("/v1/api/usuarios").with(pastorAdmin).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "pessoaId": "%s", "email": "%s@exemplo.com", "senha": "SenhaForte@123", "roleIds": ["%s"] }"""
                                    .formatted(pessoaId, papel.toLowerCase(), idDaRole(papel))))
                    .andExpect(status().isForbidden());
        }

        // não altera nem inativa um ADMIN
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/v1/api/usuarios/" + adminId)
                        .with(pastorAdmin))
                .andExpect(status().isForbidden());
    }

    @Test
    void trocarOPapelDeQuemTemMaisDeUmVinculoParaPapelComum_ebarrado() throws Exception {
        String[] a = filialComCongregacao();
        String[] b = filialComCongregacao();
        String pastorId = criarUsuario(a[1], "Pastor Três", "pastor3@exemplo.com", "PASTOR");
        mockMvc.perform(put("/v1/api/usuarios/" + pastorId + "/agrupamentos").contentType(MediaType.APPLICATION_JSON)
                        .content(corpoEscopo(a[0], b[0])))
                .andExpect(status().isOk());
        String pessoaId = JsonPath.read(mockMvc.perform(get("/v1/api/usuarios/" + pastorId))
                .andReturn().getResponse().getContentAsString(), "$.pessoaId");

        mockMvc.perform(put("/v1/api/usuarios/" + pastorId).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "pessoaId": "%s", "email": "pastor3@exemplo.com", "senha": "SenhaForte@123", "roleIds": ["%s"] }"""
                                .formatted(pessoaId, idDaRole("LIDER_MOCIDADE"))))
                .andExpect(status().isConflict());
    }

    @Test
    void escopoDeUsuarioInexistente_retorna404() throws Exception {
        mockMvc.perform(get("/v1/api/usuarios/" + UUID.randomUUID() + "/agrupamentos")).andExpect(status().isNotFound());
    }
}
