package com.registraai.registro_coristas_api.role;

import com.registraai.registro_coristas_api.suporte.UsuarioTesteFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fluxo de papéis: solicitar (pendente e inativo), aprovar ou reprovar, e dar as permissões ao papel aprovado. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RoleSolicitacaoIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    private final JsonMapper json = JsonMapper.builder().build();

    private static RequestPostProcessor comPermissoes(String... permissoes) {
        return SecurityMockMvcRequestPostProcessors.securityContext(
                UsuarioTesteFactory.contexto(new String[]{"PAPEL_DE_TESTE"}, false, permissoes));
    }

    private static final RequestPostProcessor SOLICITANTE = comPermissoes("ROLE_SOLICITAR");
    private static final RequestPostProcessor PASTOR = comPermissoes("ROLE_APROVAR", "ROLE_GERENCIAR_PERMISSOES");

    private ResultActions solicitar(String nome) throws Exception {
        return mockMvc.perform(post("/v1/api/roles/solicitacoes").with(SOLICITANTE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"nome\": \"%s\", \"descricao\": \"Papel de teste\" }".formatted(nome)));
    }

    private String idDe(ResultActions resposta) throws Exception {
        JsonNode corpo = json.readTree(resposta.andReturn().getResponse().getContentAsString());
        return corpo.get("id").asString();
    }

    @Test
    void solicitar_nasceNaoPendenteEInativo_comQuemPediu() throws Exception {
        solicitar("coordenador_de_eventos")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("COORDENADOR_DE_EVENTOS"))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.ativo").value(false))
                .andExpect(jsonPath("$.solicitadoPor").isNotEmpty());
    }

    @Test
    void solicitar_comNomeQueJaExiste_retorna409() throws Exception {
        solicitar("PASTOR").andExpect(status().isConflict());
    }

    @Test
    void solicitar_semAPermissao_retorna403() throws Exception {
        mockMvc.perform(post("/v1/api/roles/solicitacoes").with(comPermissoes("CORISTA_LER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nome\": \"INVASOR\", \"descricao\": \"x\" }"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aprovar_ativaOPapel_eNaoSePodeDecidirDeNovo() throws Exception {
        String id = idDe(solicitar("SECRETARIA_DA_MOCIDADE"));

        mockMvc.perform(patch("/v1/api/roles/" + id + "/aprovar").with(PASTOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADA"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.decididoPor").isNotEmpty());

        mockMvc.perform(patch("/v1/api/roles/" + id + "/aprovar").with(PASTOR)).andExpect(status().isConflict());
        mockMvc.perform(patch("/v1/api/roles/" + id + "/reprovar").with(PASTOR)).andExpect(status().isConflict());
    }

    @Test
    void reprovar_mantemInativo_eNaoVoltaPorReativar() throws Exception {
        String id = idDe(solicitar("FOTOGRAFO"));

        mockMvc.perform(patch("/v1/api/roles/" + id + "/reprovar").with(PASTOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REPROVADA"))
                .andExpect(jsonPath("$.ativo").value(false));

        mockMvc.perform(patch("/v1/api/roles/" + id + "/reativar").with(PASTOR)).andExpect(status().isConflict());
    }

    @Test
    void listar_porStatus_mostraOsPendentes() throws Exception {
        String id = idDe(solicitar("TESOUREIRO"));

        mockMvc.perform(get("/v1/api/roles").param("status", "PENDENTE").with(PASTOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(id)).isNotEmpty())
                .andExpect(jsonPath("$[?(@.status != 'PENDENTE')]").isEmpty());
    }

    @Test
    void permissoes_definirSubstituiOConjunto_eRejeitaNomesInexistentes() throws Exception {
        String id = idDe(solicitar("CERIMONIALISTA"));
        mockMvc.perform(patch("/v1/api/roles/" + id + "/aprovar").with(PASTOR)).andExpect(status().isOk());

        mockMvc.perform(get("/v1/api/roles/" + id + "/permissoes").with(PASTOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissoes").isEmpty());

        mockMvc.perform(put("/v1/api/roles/" + id + "/permissoes").with(PASTOR).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"permissoes\": [\"CORISTA_LER\", \"DASHBOARD_LER\"] }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissoes[0]").value("CORISTA_LER"))
                .andExpect(jsonPath("$.permissoes[1]").value("DASHBOARD_LER"));

        // substitui: não soma
        mockMvc.perform(put("/v1/api/roles/" + id + "/permissoes").with(PASTOR).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"permissoes\": [\"DASHBOARD_LER\"] }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissoes.length()").value(1));

        mockMvc.perform(put("/v1/api/roles/" + id + "/permissoes").with(PASTOR).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"permissoes\": [\"DASHBOARD_LER\", \"VOAR_DE_VASSOURA\"] }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("VOAR_DE_VASSOURA")));
    }

    @Test
    void permissoes_doAdmin_naoSaoEditaveis() throws Exception {
        JsonNode roles = json.readTree(mockMvc.perform(get("/v1/api/roles").with(PASTOR))
                .andReturn().getResponse().getContentAsString());
        String adminId = null;
        for (JsonNode role : roles) {
            if ("ADMIN".equals(role.get("nome").asString())) {
                adminId = role.get("id").asString();
            }
        }

        mockMvc.perform(put("/v1/api/roles/" + adminId + "/permissoes").with(PASTOR)
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"permissoes\": [] }"))
                .andExpect(status().isConflict());
    }
}
