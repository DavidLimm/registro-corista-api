package com.registraai.registro_coristas_api.corista;

import com.registraai.registro_coristas_api.suporte.UsuarioTesteFactory;
import com.registraai.registro_coristas_api.suporte.HierarquiaTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Nenhum corista é líder: ao receber um papel de liderança, o cadastro de corista é desligado (não apagado). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DesligamentoDeCoristaIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    private static final String SENHA = "SenhaForte@123";
    private static final String SENHA_DO_LIDER = "SenhaLider@456";
    private static final LocalDate NASCIMENTO = LocalDate.now(ZoneId.of("America/Recife")).minusYears(30).minusMonths(3);

    @Autowired
    private MockMvc mockMvc;

    private final JsonMapper json = JsonMapper.builder().build();

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

    private JsonNode corpoDe(ResultActions acao) throws Exception {
        return json.readTree(acao.andReturn().getResponse().getContentAsString());
    }

    private String idDoPapel(String nome) throws Exception {
        for (JsonNode role : corpoDe(mockMvc.perform(get("/v1/api/roles").with(admin())).andExpect(status().isOk()))) {
            if (nome.equals(role.get("nome").asString())) {
                return role.get("id").asString();
            }
        }
        throw new AssertionError("papel não encontrado: " + nome);
    }

    private String idDoUsuario(String email) throws Exception {
        for (JsonNode usuario : corpoDe(mockMvc.perform(get("/v1/api/usuarios").param("size", "100").with(admin()))
                .andExpect(status().isOk())).get("content")) {
            if (email.equals(usuario.get("email").asString())) {
                return usuario.get("id").asString();
            }
        }
        throw new AssertionError("usuário não encontrado: " + email);
    }

    /** Auto-cadastra e aprova. Devolve {coristaId, pessoaId}. */
    private String[] coristaAprovado(String congregacaoId, String nome, String email) throws Exception {
        String corpo = """
                { "pessoa": { "nome": "%s", "dataNascimento": "%s", "congregacaoId": "%s" },
                  "tipoVoz": "SOPRANO", "tamanhoCamisa": "M",
                  "usuario": { "email": "%s", "senha": "%s" } }""".formatted(nome, NASCIMENTO, congregacaoId, email, SENHA);
        JsonNode corista = corpoDe(mockMvc.perform(post("/v1/api/coristas").contentType(MediaType.APPLICATION_JSON)
                .content(corpo)).andExpect(status().isCreated()));
        String coristaId = corista.get("id").asString();
        mockMvc.perform(patch("/v1/api/coristas/" + coristaId + "/aprovar").with(admin())).andExpect(status().isOk());
        return new String[]{coristaId, corista.get("pessoa").get("id").asString()};
    }

    private ResultActions trocarPapeis(String usuarioId, String pessoaId, String email, String... roleIds)
            throws Exception {
        String roles = "[\"" + String.join("\", \"", roleIds) + "\"]";
        return mockMvc.perform(put("/v1/api/usuarios/" + usuarioId).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"pessoaId\": \"%s\", \"email\": \"%s\", \"senha\": \"%s\", \"roleIds\": %s }"
                        .formatted(pessoaId, email, SENHA_DO_LIDER, roles)));
    }

    @Test
    void quemViraLider_temOCadastroDeCoristaDesligado_masContinuaComLogin() throws Exception {
        String congregacaoId = criarCongregacao(1);
        String[] ana = coristaAprovado(congregacaoId, "Ana", "ana@exemplo.com");
        String[] bia = coristaAprovado(congregacaoId, "Bia", "bia@exemplo.com");
        String usuarioId = idDoUsuario("ana@exemplo.com");

        trocarPapeis(usuarioId, ana[1], "ana@exemplo.com", idDoPapel("LIDER_JOVENS")).andExpect(status().isOk());

        // o cadastro de corista dela some das listas e do acesso, mas não foi apagado
        mockMvc.perform(get("/v1/api/coristas/" + ana[0]).with(admin())).andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/api/coristas").param("size", "100").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(ana[0])).isEmpty())
                .andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(bia[0])).isNotEmpty());

        // ela continua sendo gente do sistema, agora como líder, e não enxerga mais um "meu cadastro" de corista
        String login = mockMvc.perform(post("/v1/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"ana@exemplo.com\", \"senha\": \"%s\" }".formatted(SENHA_DO_LIDER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[?(@ == 'ROLE_LIDER_JOVENS')]").exists())
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(login).get("token").asString();
        mockMvc.perform(get("/v1/api/coristas/meu").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        // a Bia, que segue corista, não é afetada
        mockMvc.perform(get("/v1/api/coristas/" + bia[0]).with(admin())).andExpect(status().isOk());
    }

    @Test
    void papelDeCorista_naoConviveComPapelDeLideranca() throws Exception {
        String congregacaoId = criarCongregacao(2);
        String[] caio = coristaAprovado(congregacaoId, "Caio", "caio@exemplo.com");
        String usuarioId = idDoUsuario("caio@exemplo.com");

        trocarPapeis(usuarioId, caio[1], "caio@exemplo.com", idDoPapel("CORISTA_JOVENS"), idDoPapel("LIDER_JOVENS"))
                .andExpect(status().isConflict());

        // e o cadastro de corista dele continua ativo, porque nada mudou
        mockMvc.perform(get("/v1/api/coristas/" + caio[0]).with(admin())).andExpect(status().isOk());
    }
}
