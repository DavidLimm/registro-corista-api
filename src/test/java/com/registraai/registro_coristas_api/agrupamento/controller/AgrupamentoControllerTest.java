package com.registraai.registro_coristas_api.agrupamento.controller;

import com.registraai.registro_coristas_api.agrupamento.dto.AgrupamentoRequest;
import com.registraai.registro_coristas_api.agrupamento.exception.AgrupamentoDuplicadoException;
import com.registraai.registro_coristas_api.agrupamento.exception.AgrupamentoNaoEncontradoException;
import com.registraai.registro_coristas_api.agrupamento.exception.AgrupamentoPossuiCongregacoesException;
import com.registraai.registro_coristas_api.agrupamento.model.Agrupamento;
import com.registraai.registro_coristas_api.agrupamento.model.TipoAgrupamento;
import com.registraai.registro_coristas_api.agrupamento.service.AgrupamentoService;
import com.registraai.registro_coristas_api.suporte.SegurancaTesteConfig;
import com.registraai.registro_coristas_api.suporte.WithUsuarioTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgrupamentoController.class)
@Import(SegurancaTesteConfig.class)
@WithUsuarioTeste
class AgrupamentoControllerTest {

    private static final String JSON_AREA = """
            { "tipo": "AREA", "numero": 75 }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgrupamentoService agrupamentoService;

    private Agrupamento area(UUID id, boolean ativo) {
        return Agrupamento.builder()
                .id(id).tipo(TipoAgrupamento.AREA).numero(75).ativo(ativo)
                .criadoEm(Instant.parse("2026-10-11T12:00:00Z")).atualizadoEm(Instant.parse("2026-10-11T12:00:00Z"))
                .build();
    }

    @Test
    void criar_retorna201ComLocationECorpo() throws Exception {
        UUID id = UUID.randomUUID();
        when(agrupamentoService.criar(any(AgrupamentoRequest.class))).thenReturn(area(id, true));

        mockMvc.perform(post("/v1/api/agrupamentos").contentType(MediaType.APPLICATION_JSON).content(JSON_AREA))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/v1/api/agrupamentos/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.tipo").value("AREA"))
                .andExpect(jsonPath("$.numero").value(75))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void criar_semTipoOuComNumeroNegativo_retorna400ENaoChamaService() throws Exception {
        mockMvc.perform(post("/v1/api/agrupamentos").contentType(MediaType.APPLICATION_JSON).content("{ \"numero\": 75 }"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/v1/api/agrupamentos").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"tipo\": \"AREA\", \"numero\": -1 }"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(agrupamentoService);
    }

    @Test
    void criar_comTipoInexistente_retorna400() throws Exception {
        mockMvc.perform(post("/v1/api/agrupamentos").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"tipo\": \"PAROQUIA\", \"nome\": \"x\" }"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(agrupamentoService);
    }

    @Test
    void criar_comNumeroDuplicado_retorna409() throws Exception {
        when(agrupamentoService.criar(any(AgrupamentoRequest.class)))
                .thenThrow(new AgrupamentoDuplicadoException("Já existe uma Área com o número 75"));

        mockMvc.perform(post("/v1/api/agrupamentos").contentType(MediaType.APPLICATION_JSON).content(JSON_AREA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Já existe uma Área com o número 75"));
    }

    @Test
    void listar_repassaOsFiltros() throws Exception {
        when(agrupamentoService.listar(TipoAgrupamento.AREA, true)).thenReturn(List.of(area(UUID.randomUUID(), true)));

        mockMvc.perform(get("/v1/api/agrupamentos").param("tipo", "AREA").param("ativo", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].numero").value(75));
    }

    @Test
    void buscarPorId_inexistente_retorna404() throws Exception {
        UUID id = UUID.randomUUID();
        when(agrupamentoService.buscarPorId(id)).thenThrow(new AgrupamentoNaoEncontradoException(id));

        mockMvc.perform(get("/v1/api/agrupamentos/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void atualizar_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(agrupamentoService.atualizar(any(UUID.class), any(AgrupamentoRequest.class))).thenReturn(area(id, true));

        mockMvc.perform(put("/v1/api/agrupamentos/" + id).contentType(MediaType.APPLICATION_JSON).content(JSON_AREA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value(75));
    }

    @Test
    void inativar_retorna204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/v1/api/agrupamentos/" + id)).andExpect(status().isNoContent());

        verify(agrupamentoService).inativar(id);
    }

    @Test
    void inativar_comCongregacoesAtivas_retorna409() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new AgrupamentoPossuiCongregacoesException("a Área 40", 13)).when(agrupamentoService).inativar(id);

        mockMvc.perform(delete("/v1/api/agrupamentos/" + id)).andExpect(status().isConflict());
    }

    @Test
    void reativar_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(agrupamentoService.reativar(id)).thenReturn(area(id, true));

        mockMvc.perform(patch("/v1/api/agrupamentos/" + id + "/reativar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));
    }
}
