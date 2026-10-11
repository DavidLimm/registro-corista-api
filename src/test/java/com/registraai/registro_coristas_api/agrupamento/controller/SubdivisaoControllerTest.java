package com.registraai.registro_coristas_api.agrupamento.controller;

import com.registraai.registro_coristas_api.agrupamento.dto.SubdivisaoRequest;
import com.registraai.registro_coristas_api.agrupamento.exception.AgrupamentoInvalidoException;
import com.registraai.registro_coristas_api.agrupamento.exception.SubdivisaoNaoEncontradaException;
import com.registraai.registro_coristas_api.agrupamento.model.Agrupamento;
import com.registraai.registro_coristas_api.agrupamento.model.Subdivisao;
import com.registraai.registro_coristas_api.agrupamento.model.TipoAgrupamento;
import com.registraai.registro_coristas_api.agrupamento.service.SubdivisaoService;
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

@WebMvcTest(SubdivisaoController.class)
@Import(SegurancaTesteConfig.class)
@WithUsuarioTeste
class SubdivisaoControllerTest {

    private final UUID agrupamentoId = UUID.randomUUID();
    private final String base = "/v1/api/agrupamentos/" + agrupamentoId + "/subdivisoes";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubdivisaoService subdivisaoService;

    private Subdivisao subdivisao(UUID id) {
        Agrupamento campo = Agrupamento.builder().id(agrupamentoId).tipo(TipoAgrupamento.CAMPO_MISSIONARIO).build();
        return Subdivisao.builder()
                .id(id).agrupamento(campo).nome("Alejandro Korn").ativo(true)
                .criadoEm(Instant.parse("2026-10-11T12:00:00Z")).atualizadoEm(Instant.parse("2026-10-11T12:00:00Z"))
                .build();
    }

    @Test
    void criar_retorna201ComLocationECorpo() throws Exception {
        UUID id = UUID.randomUUID();
        when(subdivisaoService.criar(any(UUID.class), any(SubdivisaoRequest.class))).thenReturn(subdivisao(id));

        mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"Alejandro Korn\" }"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost" + base + "/" + id))
                .andExpect(jsonPath("$.agrupamentoId").value(agrupamentoId.toString()))
                .andExpect(jsonPath("$.nome").value("Alejandro Korn"));
    }

    @Test
    void criar_comNumeroNegativo_retorna400ENaoChamaService() throws Exception {
        mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON).content("{ \"numero\": -3 }"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(subdivisaoService);
    }

    @Test
    void criar_regraDeTipoViolada_retorna400() throws Exception {
        when(subdivisaoService.criar(any(UUID.class), any(SubdivisaoRequest.class)))
                .thenThrow(new AgrupamentoInvalidoException("A Área tem uma única subdivisão implícita"));

        mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON).content("{ \"numero\": 1 }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listar_retorna200() throws Exception {
        when(subdivisaoService.listar(agrupamentoId, true)).thenReturn(List.of(subdivisao(UUID.randomUUID())));

        mockMvc.perform(get(base).param("ativo", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Alejandro Korn"));
    }

    @Test
    void buscar_inexistente_retorna404() throws Exception {
        UUID id = UUID.randomUUID();
        when(subdivisaoService.buscar(agrupamentoId, id)).thenThrow(new SubdivisaoNaoEncontradaException(id));

        mockMvc.perform(get(base + "/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void atualizar_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(subdivisaoService.atualizar(any(UUID.class), any(UUID.class), any(SubdivisaoRequest.class))).thenReturn(subdivisao(id));

        mockMvc.perform(put(base + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"Alejandro Korn\" }"))
                .andExpect(status().isOk());
    }

    @Test
    void inativar_retorna204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete(base + "/" + id)).andExpect(status().isNoContent());

        verify(subdivisaoService).inativar(agrupamentoId, id);
    }

    @Test
    void reativar_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(subdivisaoService.reativar(agrupamentoId, id)).thenReturn(subdivisao(id));

        mockMvc.perform(patch(base + "/" + id + "/reativar")).andExpect(status().isOk());
    }
}
