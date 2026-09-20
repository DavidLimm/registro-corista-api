package com.registraai.registro_coristas_api.endereco.controller;

import com.registraai.registro_coristas_api.endereco.dto.EnderecoRequest;
import com.registraai.registro_coristas_api.endereco.exception.EnderecoNaoEncontradoException;
import com.registraai.registro_coristas_api.endereco.model.Endereco;
import com.registraai.registro_coristas_api.endereco.service.EnderecoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EnderecoController.class)
class EnderecoControllerTest {

    private static final String JSON_VALIDO = """
            {
              "logradouro": "Rua da Aurora",
              "numero": "123",
              "complemento": "Apto 4",
              "bairro": "Boa Vista",
              "cidade": "Recife",
              "uf": "PE",
              "cep": "50050-000"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EnderecoService enderecoService;

    private Endereco enderecoPersistido(UUID id) {
        return Endereco.builder()
                .id(id).logradouro("Rua da Aurora").numero("123").complemento("Apto 4").bairro("Boa Vista")
                .cidade("Recife").uf("PE").cep("50050000")
                .criadoEm(Instant.parse("2026-09-20T12:00:00Z")).atualizadoEm(Instant.parse("2026-09-20T12:00:00Z"))
                .build();
    }

    @Test
    void criar_retorna201ComLocationECorpo() throws Exception {
        UUID id = UUID.randomUUID();
        when(enderecoService.criar(any(EnderecoRequest.class))).thenReturn(enderecoPersistido(id));

        mockMvc.perform(post("/v1/api/enderecos").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/v1/api/enderecos/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.cep").value("50050000"))
                .andExpect(jsonPath("$.uf").value("PE"));
    }

    @Test
    void criar_semObrigatoriosOuComFormatoInvalido_retorna400ENaoChamaService() throws Exception {
        String jsonInvalido = """
                { "logradouro": "", "bairro": "Boa Vista", "cidade": "Recife", "uf": "PER", "cep": "5005-0000" }
                """;

        mockMvc.perform(post("/v1/api/enderecos").contentType(MediaType.APPLICATION_JSON).content(jsonInvalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(enderecoService);
    }

    @Test
    void criar_cepOpcional_aceitaAusente() throws Exception {
        UUID id = UUID.randomUUID();
        when(enderecoService.criar(any(EnderecoRequest.class))).thenReturn(enderecoPersistido(id));
        String jsonSemCep = """
                { "logradouro": "Sítio Bom Jesus", "bairro": "Zona Rural", "cidade": "Vitória", "uf": "PE" }
                """;

        mockMvc.perform(post("/v1/api/enderecos").contentType(MediaType.APPLICATION_JSON).content(jsonSemCep))
                .andExpect(status().isCreated());
    }

    @Test
    void buscarPorId_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(enderecoService.buscarPorId(id)).thenReturn(enderecoPersistido(id));

        mockMvc.perform(get("/v1/api/enderecos/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logradouro").value("Rua da Aurora"))
                .andExpect(jsonPath("$.numero").value("123"));
    }

    @Test
    void buscarPorId_inexistente_retorna404ComProblemDetails() throws Exception {
        UUID id = UUID.randomUUID();
        when(enderecoService.buscarPorId(id)).thenThrow(new EnderecoNaoEncontradoException(id));

        mockMvc.perform(get("/v1/api/enderecos/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Endereço não encontrado: " + id));
    }

    @Test
    void buscarPorId_comIdMalFormado_retorna400() throws Exception {
        mockMvc.perform(get("/v1/api/enderecos/{id}", "nao-e-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void atualizar_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(enderecoService.atualizar(eq(id), any(EnderecoRequest.class))).thenReturn(enderecoPersistido(id));

        mockMvc.perform(put("/v1/api/enderecos/{id}", id).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void atualizar_inexistente_retorna404() throws Exception {
        UUID id = UUID.randomUUID();
        when(enderecoService.atualizar(eq(id), any(EnderecoRequest.class)))
                .thenThrow(new EnderecoNaoEncontradoException(id));

        mockMvc.perform(put("/v1/api/enderecos/{id}", id).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isNotFound());
    }
}
