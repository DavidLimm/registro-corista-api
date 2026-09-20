package com.registraai.registro_coristas_api.area;

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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fluxo completo Controller → Service → Repository → Postgres real (Flyway aplica o schema). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AreaIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    private String criarArea(int numero, String nome) throws Exception {
        return mockMvc.perform(post("/v1/api/areas").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"numero\": %d, \"nome\": \"%s\" }".formatted(numero, nome)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
    }

    @Test
    void criarBuscarEAtualizar() throws Exception {
        String location = criarArea(40, "  Área 40  ");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value(40))
                .andExpect(jsonPath("$.nome").value("Área 40"))
                .andExpect(jsonPath("$.ativa").value(true));

        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"numero\": 40, \"nome\": \"Área Quarenta\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Área Quarenta"));
    }

    @Test
    void numeroDuplicado_retorna409NaCriacaoENaAtualizacao() throws Exception {
        criarArea(10, "Área 10");
        String locationOutra = criarArea(11, "Área 11");

        mockMvc.perform(post("/v1/api/areas").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"numero\": 10, \"nome\": \"Repetida\" }"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(put(locationOutra).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"numero\": 10, \"nome\": \"Área 11\" }"))
                .andExpect(status().isConflict());

        // regravar o próprio número não é conflito
        mockMvc.perform(put(locationOutra).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"numero\": 11, \"nome\": \"Área 11 renomeada\" }"))
                .andExpect(status().isOk());
    }

    @Test
    void inativarEReativar_fazSoftDeleteEAfetaOFiltroDaListagem() throws Exception {
        String location = criarArea(20, "Área 20");

        mockMvc.perform(delete(location)).andExpect(status().isNoContent());

        // continua existindo, agora inativa
        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativa").value(false));
        mockMvc.perform(get("/v1/api/areas").param("ativa", "true"))
                .andExpect(jsonPath("$[*].numero", not(hasItem(20))));
        mockMvc.perform(get("/v1/api/areas").param("ativa", "false"))
                .andExpect(jsonPath("$[*].numero", hasItem(20)));

        mockMvc.perform(patch(location + "/reativar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativa").value(true));
        mockMvc.perform(get("/v1/api/areas").param("ativa", "true"))
                .andExpect(jsonPath("$[*].numero", hasItem(20)));
    }

    @Test
    void buscarInexistente_retorna404() throws Exception {
        mockMvc.perform(get("/v1/api/areas/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
