package com.registraai.registro_coristas_api.endereco;

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

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fluxo completo Controller → Service → Repository → Postgres real (Flyway aplica o schema). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EnderecoIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void criarBuscarEAtualizar_normalizandoCepEUf() throws Exception {
        String corpoCriacao = """
                { "logradouro": "  Rua da Aurora ", "numero": "123", "complemento": "  ",
                  "bairro": "Boa Vista", "cidade": "Recife", "uf": "pe", "cep": "50050-000" }
                """;

        String location = mockMvc.perform(post("/v1/api/enderecos")
                        .contentType(MediaType.APPLICATION_JSON).content(corpoCriacao))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.logradouro").value("Rua da Aurora"))
                .andExpect(jsonPath("$.complemento").doesNotExist())
                .andExpect(jsonPath("$.uf").value("PE"))
                .andExpect(jsonPath("$.cep").value("50050000"))
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("123"))
                .andExpect(jsonPath("$.cep").value("50050000"));

        String corpoAtualizacao = """
                { "logradouro": "Rua Nova", "numero": "S/N", "complemento": "Fundos",
                  "bairro": "Centro", "cidade": "Olinda", "uf": "PE", "cep": "53020000" }
                """;

        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON).content(corpoAtualizacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logradouro").value("Rua Nova"))
                .andExpect(jsonPath("$.complemento").value("Fundos"))
                .andExpect(jsonPath("$.cep").value("53020000"));

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cidade").value("Olinda"));
    }

    @Test
    void buscarInexistente_retorna404() throws Exception {
        mockMvc.perform(get("/v1/api/enderecos/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
