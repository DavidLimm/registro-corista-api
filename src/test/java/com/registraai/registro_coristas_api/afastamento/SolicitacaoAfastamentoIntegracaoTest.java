package com.registraai.registro_coristas_api.afastamento;

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

import java.time.LocalDate;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Quem só pede o afastamento (liderança de cada lista) e quem decide (LIDER_MOCIDADE/PASTOR): pedido, fila e decisão. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SolicitacaoAfastamentoIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    private static final String SENHA = "SenhaForte@123";
    private static final LocalDate NASCIMENTO = LocalDate.now(ZoneId.of("America/Recife")).minusYears(30).minusMonths(3);

    @Autowired
    private MockMvc mockMvc;

    private final JsonMapper json = JsonMapper.builder().build();

    private static RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.securityContext(
                UsuarioTesteFactory.contexto(new String[]{"ADMIN"}, true, new String[0]));
    }

    /** Líder de jovens: pode pedir o afastamento, mas não decidir. */
    private static final RequestPostProcessor LIDER_DE_JOVENS = SecurityMockMvcRequestPostProcessors.securityContext(
            UsuarioTesteFactory.contexto(new String[]{"LIDER_JOVENS"}, false, new String[]{"CORISTA_SOLICITAR_AFASTAMENTO"}));
    private static final RequestPostProcessor LIDER_DE_ADOLESCENTES = SecurityMockMvcRequestPostProcessors.securityContext(
            UsuarioTesteFactory.contexto(new String[]{"LIDER_ADOLESCENTES"}, false, new String[]{"CORISTA_SOLICITAR_AFASTAMENTO"}));
    private static final RequestPostProcessor LIDER_DA_MOCIDADE = SecurityMockMvcRequestPostProcessors.securityContext(
            UsuarioTesteFactory.contexto(new String[]{"LIDER_MOCIDADE"}, false, new String[]{"CORISTA_APROVAR_AFASTAMENTO"}));

    private String idDoLocation(String location) {
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private String criarCongregacao(int numeroDaArea) throws Exception {
        String areaLocation = mockMvc.perform(post("/v1/api/areas").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"numero\": %d, \"nome\": \"Área %d\" }".formatted(numeroDaArea, numeroDaArea)))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        String congregacaoLocation = mockMvc.perform(post("/v1/api/congregacoes").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"areaId\": \"%s\", \"nome\": \"Sede %d\" }"
                                .formatted(idDoLocation(areaLocation), numeroDaArea)))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        return idDoLocation(congregacaoLocation);
    }

    /** Auto-cadastra e aprova um corista jovem; devolve o id do corista. */
    private String coristaAprovado(String congregacaoId, String nome) throws Exception {
        String corpo = """
                { "pessoa": { "nome": "%s", "dataNascimento": "%s", "congregacaoId": "%s" },
                  "tipoVoz": "SOPRANO", "tamanhoCamisa": "M",
                  "usuario": { "email": "%s@exemplo.com", "senha": "%s" } }"""
                .formatted(nome, NASCIMENTO, congregacaoId, nome.toLowerCase(), SENHA);
        String resposta = mockMvc.perform(post("/v1/api/coristas").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = json.readTree(resposta).get("id").asString();
        mockMvc.perform(patch("/v1/api/coristas/" + id + "/aprovar").with(admin())).andExpect(status().isOk());
        return id;
    }

    private ResultActions solicitar(RequestPostProcessor quem, String coristaId) throws Exception {
        return mockMvc.perform(post("/v1/api/coristas/" + coristaId + "/solicitar-afastamento").with(quem)
                .contentType(MediaType.APPLICATION_JSON).content("{ \"motivo\": \"Viagem longa\" }"));
    }

    private String idDe(ResultActions resposta) throws Exception {
        JsonNode corpo = json.readTree(resposta.andReturn().getResponse().getContentAsString());
        return corpo.get("id").asString();
    }

    @Test
    void liderPedeEOutroAprova_apessoaFicaAfastadaESemLogin() throws Exception {
        String congregacaoId = criarCongregacao(1);
        String coristaId = coristaAprovado(congregacaoId, "Ana");

        String pedidoId = idDe(solicitar(LIDER_DE_JOVENS, coristaId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.pessoaNome").value("Ana"))
                .andExpect(jsonPath("$.motivo").value("Viagem longa")));

        mockMvc.perform(get("/v1/api/solicitacoes-afastamento").param("status", "PENDENTE").with(LIDER_DA_MOCIDADE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(pedidoId)).isNotEmpty());

        mockMvc.perform(patch("/v1/api/solicitacoes-afastamento/" + pedidoId + "/aprovar").with(LIDER_DA_MOCIDADE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADA"))
                .andExpect(jsonPath("$.decididoPor").isNotEmpty());

        mockMvc.perform(get("/v1/api/coristas/" + coristaId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pessoa.status").value("AFASTADO"));
        mockMvc.perform(post("/v1/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"ana@exemplo.com\", \"senha\": \"%s\" }".formatted(SENHA)))
                .andExpect(status().isForbidden());

        // quem já está afastado não pode ser afastado de novo, e o pedido decidido não é decidido outra vez
        solicitar(LIDER_DE_JOVENS, coristaId).andExpect(status().isConflict());
        mockMvc.perform(patch("/v1/api/solicitacoes-afastamento/" + pedidoId + "/negar").with(LIDER_DA_MOCIDADE))
                .andExpect(status().isConflict());
    }

    @Test
    void negar_mantemAPessoaAprovada_ePermiteUmNovoPedidoDepois() throws Exception {
        String congregacaoId = criarCongregacao(2);
        String coristaId = coristaAprovado(congregacaoId, "Bia");
        String pedidoId = idDe(solicitar(LIDER_DE_JOVENS, coristaId).andExpect(status().isCreated()));

        // um pedido pendente por pessoa
        solicitar(LIDER_DE_JOVENS, coristaId).andExpect(status().isConflict());

        mockMvc.perform(patch("/v1/api/solicitacoes-afastamento/" + pedidoId + "/negar").with(LIDER_DA_MOCIDADE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEGADA"));
        mockMvc.perform(get("/v1/api/coristas/" + coristaId).with(admin()))
                .andExpect(jsonPath("$.pessoa.status").value("APROVADO"));

        solicitar(LIDER_DE_JOVENS, coristaId).andExpect(status().isCreated());
    }

    @Test
    void pedido_respeitaORecorteEAPermissao() throws Exception {
        String congregacaoId = criarCongregacao(3);
        String coristaId = coristaAprovado(congregacaoId, "Caio");

        // líder de adolescentes não enxerga corista da lista de jovens
        solicitar(LIDER_DE_ADOLESCENTES, coristaId).andExpect(status().isNotFound());
        // quem só aprova não pede, e quem só pede não decide
        solicitar(LIDER_DA_MOCIDADE, coristaId).andExpect(status().isForbidden());
        String pedidoId = idDe(solicitar(LIDER_DE_JOVENS, coristaId).andExpect(status().isCreated()));
        mockMvc.perform(patch("/v1/api/solicitacoes-afastamento/" + pedidoId + "/aprovar").with(LIDER_DE_JOVENS))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/api/solicitacoes-afastamento").with(LIDER_DE_JOVENS)).andExpect(status().isForbidden());
    }

    @Test
    void pedido_semMotivoOuDeCoristaInexistente_naoPassa() throws Exception {
        mockMvc.perform(post("/v1/api/coristas/00000000-0000-0000-0000-000000000000/solicitar-afastamento")
                        .with(LIDER_DE_JOVENS).contentType(MediaType.APPLICATION_JSON).content("{ \"motivo\": \"x\" }"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/v1/api/coristas/00000000-0000-0000-0000-000000000000/solicitar-afastamento")
                        .with(LIDER_DE_JOVENS).contentType(MediaType.APPLICATION_JSON).content("{ \"motivo\": \"  \" }"))
                .andExpect(status().isBadRequest());
    }
}
