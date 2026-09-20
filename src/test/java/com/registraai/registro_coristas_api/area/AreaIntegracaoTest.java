package com.registraai.registro_coristas_api.area;

import com.registraai.registro_coristas_api.area.model.Area;
import com.registraai.registro_coristas_api.area.repository.AreaRepository;
import com.registraai.registro_coristas_api.congregacao.model.Congregacao;
import com.registraai.registro_coristas_api.congregacao.repository.CongregacaoRepository;
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

import static org.hamcrest.Matchers.containsString;
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

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private CongregacaoRepository congregacaoRepository;

    private Area buscarArea(int numero) {
        return areaRepository.findAllByOrderByNumeroAsc().stream()
                .filter(area -> area.getNumero() == numero)
                .findFirst().orElseThrow();
    }

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
    void inativar_comCongregacaoAtiva_retorna409ESoLiberaDepoisDeRemanejar() throws Exception {
        String locationOrigem = criarArea(30, "Área 30");
        String locationDestino = criarArea(31, "Área 31");
        Area origem = buscarArea(30);
        Area destino = buscarArea(31);
        Congregacao congregacao = congregacaoRepository.save(
                Congregacao.builder().area(origem).nome("Sede da Área 30").build());

        mockMvc.perform(delete(locationOrigem))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("existe 1 congregação ativa")));
        mockMvc.perform(get(locationOrigem))
                .andExpect(jsonPath("$.ativa").value(true));

        // remanejar a congregação para outra área libera a inativação
        congregacao.setArea(destino);
        congregacaoRepository.save(congregacao);

        mockMvc.perform(delete(locationOrigem)).andExpect(status().isNoContent());
        mockMvc.perform(get(locationOrigem)).andExpect(jsonPath("$.ativa").value(false));
        // a área que recebeu a congregação agora é a que não pode ser inativada
        mockMvc.perform(delete(locationDestino)).andExpect(status().isConflict());
    }

    @Test
    void inativar_congregacaoInativaNaoBloqueia() throws Exception {
        String location = criarArea(32, "Área 32");
        Area area = buscarArea(32);
        congregacaoRepository.save(Congregacao.builder().area(area).nome("Fechada").ativa(false).build());

        mockMvc.perform(delete(location)).andExpect(status().isNoContent());
    }

    @Test
    void buscarInexistente_retorna404() throws Exception {
        mockMvc.perform(get("/v1/api/areas/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
