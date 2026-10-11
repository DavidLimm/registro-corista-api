package com.registraai.registro_coristas_api.agrupamento;

import com.jayway.jsonpath.JsonPath;
import com.registraai.registro_coristas_api.suporte.UsuarioTesteFactory;
import com.registraai.registro_coristas_api.suporte.WithUsuarioTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fluxo completo Controller → Service → Repository → Postgres real (Flyway aplica o schema e o seed das 74 áreas). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@WithUsuarioTeste
class AgrupamentoIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    private String criar(String json) throws Exception {
        return mockMvc.perform(post("/v1/api/agrupamentos").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
    }

    @Test
    void as74AreasJaVemDoBanco() throws Exception {
        mockMvc.perform(get("/v1/api/agrupamentos").param("tipo", "AREA"))
                .andExpect(status().isOk())
                // o banco é compartilhado entre os testes da classe: outros podem ter acrescentado áreas acima da 74
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(74))))
                .andExpect(jsonPath("$[0].numero").value(1))
                .andExpect(jsonPath("$[73].numero").value(74));
    }

    @Test
    @WithAnonymousUser
    void leituraEPublica_mas_escritaExigeLogin() throws Exception {
        String id = JsonPath.read(mockMvc.perform(get("/v1/api/agrupamentos").param("tipo", "AREA"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$[0].id");

        mockMvc.perform(get("/v1/api/agrupamentos/" + id)).andExpect(status().isOk());
        mockMvc.perform(get("/v1/api/agrupamentos/" + id + "/subdivisoes")).andExpect(status().isOk());
        mockMvc.perform(post("/v1/api/agrupamentos").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"tipo\": \"AREA\", \"numero\": 90 }"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void escritaSemAPermissaoAgrupamentoGerenciar_retorna403() throws Exception {
        var semPermissao = SecurityMockMvcRequestPostProcessors.securityContext(
                UsuarioTesteFactory.contexto(new String[]{"PAPEL_SEM_PODER"}, false, new String[]{"CORISTA_LER"}));
        String id = "/v1/api/agrupamentos/" + UUID.randomUUID();

        mockMvc.perform(post("/v1/api/agrupamentos").with(semPermissao).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"tipo\": \"AREA\", \"numero\": 91 }"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(id).with(semPermissao).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"tipo\": \"AREA\", \"numero\": 91 }"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(id + "/subdivisoes").with(semPermissao).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"numero\": 1 }"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(id + "/subdivisoes/" + UUID.randomUUID()).with(semPermissao).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"numero\": 1 }"))
                .andExpect(status().isForbidden());
    }

    @Test
    void areaJaSemeada_naoPodeSerCriadaDeNovo() throws Exception {
        mockMvc.perform(post("/v1/api/agrupamentos").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"tipo\": \"AREA\", \"numero\": 40 }"))
                .andExpect(status().isConflict());
    }

    @Test
    void acrescentarAArea75_criaASubdivisaoImplicita() throws Exception {
        String location = criar("{ \"tipo\": \"AREA\", \"numero\": 75 }");

        mockMvc.perform(get(location + "/subdivisoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].numero").doesNotExist())
                .andExpect(jsonPath("$[0].nome").doesNotExist());
    }

    @Test
    void areaNaoAceitaNomeNemSubdivisaoManual() throws Exception {
        mockMvc.perform(post("/v1/api/agrupamentos").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"tipo\": \"AREA\", \"numero\": 80, \"nome\": \"x\" }"))
                .andExpect(status().isBadRequest());

        String location = criar("{ \"tipo\": \"AREA\", \"numero\": 81 }");
        mockMvc.perform(post(location + "/subdivisoes").contentType(MediaType.APPLICATION_JSON).content("{ \"numero\": 1 }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void campoMissionarioComLocais() throws Exception {
        String campo = criar("{ \"tipo\": \"CAMPO_MISSIONARIO\", \"nome\": \"Argentina\", \"pais\": \"Argentina\" }");

        mockMvc.perform(post(campo + "/subdivisoes").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nome\": \"Alejandro Korn\" }"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Alejandro Korn"));
        mockMvc.perform(post(campo + "/subdivisoes").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nome\": \"alejandro korn\" }"))
                .andExpect(status().isConflict());
        mockMvc.perform(post(campo + "/subdivisoes").contentType(MediaType.APPLICATION_JSON).content("{ \"numero\": 1 }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void campoMissionarioSemPais_retorna400() throws Exception {
        mockMvc.perform(post("/v1/api/agrupamentos").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"tipo\": \"CAMPO_MISSIONARIO\", \"nome\": \"Paraguai\" }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setorEFilialComAreasNumeradas_numerosSeRepetemEntreAgrupamentos() throws Exception {
        String setor = criar("{ \"tipo\": \"SETOR\", \"numero\": 1, \"nome\": \"Cabo de Santo Agostinho\" }");
        String filial = criar("{ \"tipo\": \"FILIAL\", \"nome\": \"Petrolina\" }");

        // Área 10 existe nos dois agrupamentos sem conflito
        for (String agrupamento : new String[]{setor, filial}) {
            mockMvc.perform(post(agrupamento + "/subdivisoes").contentType(MediaType.APPLICATION_JSON).content("{ \"numero\": 10 }"))
                    .andExpect(status().isCreated());
        }
        // mas não duas vezes no mesmo
        mockMvc.perform(post(setor + "/subdivisoes").contentType(MediaType.APPLICATION_JSON).content("{ \"numero\": 10 }"))
                .andExpect(status().isConflict());
        // número de setor é único entre setores
        mockMvc.perform(post("/v1/api/agrupamentos").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"tipo\": \"SETOR\", \"numero\": 1, \"nome\": \"Outro\" }"))
                .andExpect(status().isConflict());
    }

    @Test
    void inativarEReativar_agrupamentoESubdivisao() throws Exception {
        String filial = criar("{ \"tipo\": \"FILIAL\", \"nome\": \"Garanhuns\" }");
        String area = mockMvc.perform(post(filial + "/subdivisoes").contentType(MediaType.APPLICATION_JSON).content("{ \"numero\": 2 }"))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");

        mockMvc.perform(delete(area)).andExpect(status().isNoContent());
        mockMvc.perform(get(area)).andExpect(jsonPath("$.ativo").value(false));
        mockMvc.perform(get(filial + "/subdivisoes").param("ativo", "true")).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(patch(area + "/reativar")).andExpect(jsonPath("$.ativo").value(true));

        mockMvc.perform(delete(filial)).andExpect(status().isNoContent());
        mockMvc.perform(get(filial)).andExpect(jsonPath("$.ativo").value(false));
        // agrupamento inativo não recebe subdivisão nova
        mockMvc.perform(post(filial + "/subdivisoes").contentType(MediaType.APPLICATION_JSON).content("{ \"numero\": 3 }"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch(filial + "/reativar")).andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void atualizar_naoTrocaOTipo() throws Exception {
        String filial = criar("{ \"tipo\": \"FILIAL\", \"nome\": \"Serra Talhada\" }");

        mockMvc.perform(put(filial).contentType(MediaType.APPLICATION_JSON).content("{ \"tipo\": \"FILIAL\", \"nome\": \"Serra Talhada PE\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Serra Talhada PE"));
        mockMvc.perform(put(filial).contentType(MediaType.APPLICATION_JSON).content("{ \"tipo\": \"SETOR\", \"numero\": 9, \"nome\": \"x\" }"))
                .andExpect(status().isBadRequest());
    }
}
