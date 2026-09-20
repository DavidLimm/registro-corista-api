package com.registraai.registro_coristas_api.congregacao;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
class CongregacaoIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    private static final String ENDERECO_JSON = """
            , "endereco": { "logradouro": "%s", "numero": "123", "bairro": "Boa Vista",
                            "cidade": "Recife", "uf": "pe", "cep": "50050-000" }""";

    /** Cria uma área pela API e devolve o id dela. */
    private String criarArea(int numero) throws Exception {
        String location = mockMvc.perform(post("/v1/api/areas").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"numero\": %d, \"nome\": \"Área %d\" }".formatted(numero, numero)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private String corpo(String areaId, String nome, String enderecoJson) {
        return "{ \"areaId\": \"%s\", \"nome\": \"%s\"%s }".formatted(areaId, nome, enderecoJson);
    }

    /** Cria uma congregação sem endereço pela API e devolve o Location. */
    private String criarCongregacao(String areaId, String nome) throws Exception {
        return mockMvc.perform(post("/v1/api/congregacoes").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(areaId, nome, "")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
    }

    @Test
    void criarBuscarEAtualizar_gravaEnderecoAninhadoNormalizadoEMantemQuandoAusente() throws Exception {
        String areaId = criarArea(1);

        MvcResult criada = mockMvc.perform(post("/v1/api/congregacoes").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(areaId, "  Sede  ", ENDERECO_JSON.formatted("Rua da Aurora"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Sede"))
                .andExpect(jsonPath("$.ativa").value(true))
                .andExpect(jsonPath("$.area.id").value(areaId))
                .andExpect(jsonPath("$.area.numero").value(1))
                .andExpect(jsonPath("$.endereco.uf").value("PE"))
                .andExpect(jsonPath("$.endereco.cep").value("50050000"))
                .andReturn();
        String location = criada.getResponse().getHeader("Location");
        String enderecoId = JsonPath.read(criada.getResponse().getContentAsString(), "$.endereco.id");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endereco.logradouro").value("Rua da Aurora"));

        // editar com novo endereço atualiza o mesmo registro de endereço
        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(areaId, "Sede Renomeada", ENDERECO_JSON.formatted("Rua Nova"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Sede Renomeada"))
                .andExpect(jsonPath("$.endereco.id").value(enderecoId))
                .andExpect(jsonPath("$.endereco.logradouro").value("Rua Nova"));

        // editar sem endereço no corpo mantém o atual
        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(areaId, "Sede Renomeada 2", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Sede Renomeada 2"))
                .andExpect(jsonPath("$.endereco.id").value(enderecoId))
                .andExpect(jsonPath("$.endereco.logradouro").value("Rua Nova"));
    }

    @Test
    void editarCongregacaoSemEnderecoComEnderecoNoCorpo_criaEVincula() throws Exception {
        String areaId = criarArea(2);
        String location = criarCongregacao(areaId, "Sem endereço");

        mockMvc.perform(get(location)).andExpect(jsonPath("$.endereco").doesNotExist());

        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(areaId, "Sem endereço", ENDERECO_JSON.formatted("Rua Criada"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endereco.logradouro").value("Rua Criada"));
    }

    @Test
    void nomeDuplicado_naMesmaAreaDa409SemDiferenciarMaiusculasEEmOutraAreaPermite() throws Exception {
        String areaA = criarArea(3);
        String areaB = criarArea(4);
        String location = criarCongregacao(areaA, "Sede");

        mockMvc.perform(post("/v1/api/congregacoes").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(areaA, "SEDE", "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("Já existe uma congregação chamada")));

        // mesmo nome em outra área é permitido
        criarCongregacao(areaB, "Sede");

        // regravar o próprio nome não é conflito
        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(areaA, "Sede", "")))
                .andExpect(status().isOk());
    }

    @Test
    void criar_emAreaInativa_retorna409EEmAreaInexistente_retorna404() throws Exception {
        String areaId = criarArea(5);
        mockMvc.perform(delete("/v1/api/areas/" + areaId)).andExpect(status().isNoContent());

        mockMvc.perform(post("/v1/api/congregacoes").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(areaId, "Sede", "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("inativa")));

        mockMvc.perform(post("/v1/api/congregacoes").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("00000000-0000-0000-0000-000000000000", "Sede", "")))
                .andExpect(status().isNotFound());
    }

    @Test
    void remanejar_liberaAInativacaoDaAreaDeOrigem() throws Exception {
        String origem = criarArea(6);
        String destino = criarArea(7);
        String location = criarCongregacao(origem, "Sede da Área 6");

        // área com congregação ativa não pode ser inativada
        mockMvc.perform(delete("/v1/api/areas/" + origem)).andExpect(status().isConflict());

        // remanejar = editar a congregação apontando para outra área ativa
        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(destino, "Sede da Área 6", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.area.id").value(destino))
                .andExpect(jsonPath("$.area.numero").value(7));

        mockMvc.perform(delete("/v1/api/areas/" + origem)).andExpect(status().isNoContent());
        // e a área que recebeu a congregação passa a ser a bloqueada
        mockMvc.perform(delete("/v1/api/areas/" + destino)).andExpect(status().isConflict());

        // não é possível remanejar de volta para a área agora inativa
        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(origem, "Sede da Área 6", "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("inativa")));
        mockMvc.perform(get(location)).andExpect(jsonPath("$.area.id").value(destino));
    }

    @Test
    void remanejar_paraAreaOndeOMesmoNomeJaExiste_retorna409() throws Exception {
        String areaA = criarArea(8);
        String areaB = criarArea(9);
        String location = criarCongregacao(areaA, "Sede");
        criarCongregacao(areaB, "Sede");

        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(areaB, "Sede", "")))
                .andExpect(status().isConflict());
    }

    @Test
    void inativarEReativar_eReativarExigeAreaAtiva() throws Exception {
        String areaId = criarArea(10);
        String location = criarCongregacao(areaId, "Sede");

        mockMvc.perform(delete(location)).andExpect(status().isNoContent());
        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativa").value(false));

        // congregação inativa não bloqueia a inativação da área
        mockMvc.perform(delete("/v1/api/areas/" + areaId)).andExpect(status().isNoContent());

        // mas não dá para reativar a congregação enquanto a área estiver inativa
        mockMvc.perform(patch(location + "/reativar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("inativa")));

        mockMvc.perform(patch("/v1/api/areas/" + areaId + "/reativar")).andExpect(status().isOk());
        mockMvc.perform(patch(location + "/reativar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativa").value(true));
    }

    @Test
    void listar_filtraPorAreaESituacaoOrdenandoPorNome() throws Exception {
        String areaId = criarArea(11);
        String outraArea = criarArea(12);
        criarCongregacao(areaId, "Bairro Novo");
        String fechada = criarCongregacao(areaId, "Antiga");
        criarCongregacao(areaId, "Centro");
        criarCongregacao(outraArea, "De outra área");
        mockMvc.perform(delete(fechada)).andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/api/congregacoes").param("areaId", areaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].nome").value("Antiga"))
                .andExpect(jsonPath("$[1].nome").value("Bairro Novo"))
                .andExpect(jsonPath("$[2].nome").value("Centro"));

        mockMvc.perform(get("/v1/api/congregacoes").param("areaId", areaId).param("ativa", "true"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nome").value("Bairro Novo"));

        mockMvc.perform(get("/v1/api/congregacoes").param("areaId", areaId).param("ativa", "false"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Antiga"));
    }

    @Test
    void buscarInexistente_retorna404() throws Exception {
        String corpoResposta = mockMvc.perform(get("/v1/api/congregacoes/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andReturn().getResponse().getContentAsString();

        assertThat(corpoResposta).contains("Congregação não encontrada");
    }
}
