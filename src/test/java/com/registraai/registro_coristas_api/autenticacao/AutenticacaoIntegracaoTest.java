package com.registraai.registro_coristas_api.autenticacao;

import com.registraai.registro_coristas_api.suporte.UsuarioTesteFactory;
import com.registraai.registro_coristas_api.suporte.HierarquiaTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Segurança de ponta a ponta, com o filtro JWT e o Postgres reais: auto-cadastro público, login, bloqueio por status
 * do cadastro, token no cabeçalho e o que um corista pode (ou não) fazer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AutenticacaoIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    private static final String SENHA = "SenhaForte@123";
    private static final LocalDate NASCIMENTO = LocalDate.now(ZoneId.of("America/Recife")).minusYears(30).minusMonths(3);

    @Autowired
    private MockMvc mockMvc;

    private final JsonMapper json = JsonMapper.builder().build();

    /** Quem prepara o cenário (área, congregação, aprovações) é um admin; o resto do teste usa o token real. */
    private static RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.securityContext(
                UsuarioTesteFactory.contexto(new String[]{"ADMIN"}, true, new String[0]));
    }

    private String idDoLocation(String location) {
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private String criarCongregacao(int numeroDaArea) throws Exception {
        String subdivisaoId = HierarquiaTeste.criarSubdivisao(mockMvc, admin());
        String congregacaoLocation = mockMvc.perform(post("/v1/api/congregacoes").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"subdivisaoId\": \"%s\", \"nome\": \"Sede %d\" }"
                                .formatted(subdivisaoId, numeroDaArea)))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        return idDoLocation(congregacaoLocation);
    }

    private String corpoDoCorista(String congregacaoId, String nome, String email, LocalDate nascimento) {
        return """
                { "pessoa": { "nome": "%s", "dataNascimento": "%s", "congregacaoId": "%s" },
                  "tipoVoz": "SOPRANO", "tamanhoCamisa": "M",
                  "usuario": { "email": "%s", "senha": "%s" } }""".formatted(nome, nascimento, congregacaoId, email, SENHA);
    }

    /** Auto-cadastro público (sem token). Devolve o id do corista, que nasce PENDENTE. */
    private String autoCadastrar(String congregacaoId, String nome, String email) throws Exception {
        String location = mockMvc.perform(post("/v1/api/coristas").contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoCorista(congregacaoId, nome, email, NASCIMENTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pessoa.status").value("PENDENTE"))
                .andReturn().getResponse().getHeader("Location");
        return idDoLocation(location);
    }

    private ResultActions login(String email, String senha) throws Exception {
        return mockMvc.perform(post("/v1/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{ \"email\": \"%s\", \"senha\": \"%s\" }".formatted(email, senha)));
    }

    private String tokenDe(String email) throws Exception {
        String corpo = login(email, SENHA).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("token").asString();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private void aprovar(String coristaId) throws Exception {
        mockMvc.perform(patch("/v1/api/coristas/" + coristaId + "/aprovar").with(admin()))
                .andExpect(status().isOk());
    }

    // ---------- login ----------

    @Test
    void cadastroPendente_loginComSenhaCertaRetorna403ComOMotivo_eComSenhaErradaRetorna401() throws Exception {
        String congregacaoId = criarCongregacao(1);
        autoCadastrar(congregacaoId, "Ana Pendente", "pendente@exemplo.com");

        login("pendente@exemplo.com", SENHA)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Seu cadastro está pendente de aprovação."));

        // sem a senha certa ninguém descobre que o e-mail existe nem em que situação está
        login("pendente@exemplo.com", "senhaErrada123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
    }

    @Test
    void emailInexistente_retornaOMesmo401DaSenhaErrada() throws Exception {
        login("ninguem@exemplo.com", SENHA)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
    }

    @Test
    void aprovado_logaComEmailEmQualquerCaixaERecebeToken() throws Exception {
        String congregacaoId = criarCongregacao(2);
        aprovar(autoCadastrar(congregacaoId, "Bia Aprovada", "bia@exemplo.com"));

        login("  BIA@Exemplo.com ", SENHA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value("bia@exemplo.com"))
                .andExpect(jsonPath("$.roles[?(@ == 'ROLE_CORISTA_JOVENS')]").exists());
    }

    @Test
    void reprovado_naoLoga() throws Exception {
        String congregacaoId = criarCongregacao(3);
        String id = autoCadastrar(congregacaoId, "Caio Reprovado", "caio@exemplo.com");
        mockMvc.perform(patch("/v1/api/coristas/" + id + "/reprovar").with(admin())).andExpect(status().isOk());

        login("caio@exemplo.com", SENHA)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(containsString("não foi aprovado")));
    }

    // ---------- token ----------

    @Test
    void semTokenOuComTokenInvalido_retorna401EmProblemDetails() throws Exception {
        mockMvc.perform(get("/v1/api/coristas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/v1/api/coristas").header(HttpHeaders.AUTHORIZATION, bearer("lixo")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/v1/api/coristas").header(HttpHeaders.AUTHORIZATION, "Basic abc"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void afastado_tokenQueAindaNaoExpirouParaDeFuncionarNaHora() throws Exception {
        String congregacaoId = criarCongregacao(4);
        String id = autoCadastrar(congregacaoId, "Duda Afastada", "duda@exemplo.com");
        aprovar(id);
        String token = tokenDe("duda@exemplo.com");

        mockMvc.perform(get("/v1/api/coristas/meu").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/v1/api/coristas/" + id + "/afastar").with(admin())).andExpect(status().isOk());

        mockMvc.perform(get("/v1/api/coristas/meu").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
        login("duda@exemplo.com", SENHA)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(containsString("afastado")));
    }

    // ---------- o que o corista pode fazer ----------

    @Test
    void corista_veSoOProprioCadastro() throws Exception {
        String congregacaoId = criarCongregacao(5);
        String idEla = autoCadastrar(congregacaoId, "Elis", "elis@exemplo.com");
        String idOutra = autoCadastrar(congregacaoId, "Fabi", "fabi@exemplo.com");
        aprovar(idEla);
        aprovar(idOutra);
        String token = bearer(tokenDe("elis@exemplo.com"));

        mockMvc.perform(get("/v1/api/coristas/meu").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idEla))
                .andExpect(jsonPath("$.pessoa.nome").value("Elis"));
        mockMvc.perform(get("/v1/api/coristas/" + idEla).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());

        // nem a lista, nem o cadastro de outro corista
        mockMvc.perform(get("/v1/api/coristas").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/api/coristas/" + idOutra).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/v1/api/coristas/" + idOutra).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoCorista(congregacaoId, "Fabi", "fabi@exemplo.com", NASCIMENTO)))
                .andExpect(status().isForbidden());
    }

    @Test
    void corista_naoSeAprovaNemMudaAPropriaDataDeNascimento_masEditaOResto() throws Exception {
        String congregacaoId = criarCongregacao(6);
        String id = autoCadastrar(congregacaoId, "Gabi", "gabi@exemplo.com");
        aprovar(id);
        String token = bearer(tokenDe("gabi@exemplo.com"));

        mockMvc.perform(patch("/v1/api/coristas/" + id + "/aprovar").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // mudar a data (ex.: para parecer mais velho) só o líder pode
        mockMvc.perform(put("/v1/api/coristas/" + id).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoCorista(congregacaoId, "Gabi", "gabi@exemplo.com", NASCIMENTO.minusYears(5))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(containsString("líder")));

        // o resto (aqui, o nome) o corista edita
        mockMvc.perform(put("/v1/api/coristas/" + id).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoCorista(congregacaoId, "Gabriela", "gabi@exemplo.com", NASCIMENTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pessoa.nome").value("Gabriela"));
    }

    @Test
    void corista_naoAcessaAgrupamentosCongregacoesAlteracoesNemUsuarios() throws Exception {
        String congregacaoId = criarCongregacao(7);
        aprovar(autoCadastrar(congregacaoId, "Hugo", "hugo@exemplo.com"));
        String token = bearer(tokenDe("hugo@exemplo.com"));

        mockMvc.perform(get("/v1/api/usuarios").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/api/agrupamentos").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"tipo\": \"AREA\", \"numero\": 99 }"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/api/pessoas").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }

    @Test
    void listaDeCongregacoesEAgrupamentos_ePublica_masOsUsuariosExigemLogin() throws Exception {
        criarCongregacao(8);

        mockMvc.perform(get("/v1/api/congregacoes"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/api/agrupamentos"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/api/usuarios"))
                .andExpect(status().isUnauthorized());
    }
}
