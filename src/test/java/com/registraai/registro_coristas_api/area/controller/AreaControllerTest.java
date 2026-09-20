package com.registraai.registro_coristas_api.area.controller;

import com.registraai.registro_coristas_api.area.dto.AreaRequest;
import com.registraai.registro_coristas_api.area.exception.AreaNaoEncontradaException;
import com.registraai.registro_coristas_api.area.exception.AreaNumeroDuplicadoException;
import com.registraai.registro_coristas_api.area.model.Area;
import com.registraai.registro_coristas_api.area.service.AreaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

@WebMvcTest(AreaController.class)
class AreaControllerTest {

    private static final String JSON_VALIDO = """
            { "numero": 40, "nome": "Área 40" }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AreaService areaService;

    private Area areaPersistida(UUID id, boolean ativa) {
        return Area.builder()
                .id(id).numero(40).nome("Área 40").ativa(ativa)
                .criadoEm(Instant.parse("2026-09-20T12:00:00Z")).atualizadoEm(Instant.parse("2026-09-20T12:00:00Z"))
                .build();
    }

    @Test
    void criar_retorna201ComLocationECorpo() throws Exception {
        UUID id = UUID.randomUUID();
        when(areaService.criar(any(AreaRequest.class))).thenReturn(areaPersistida(id, true));

        mockMvc.perform(post("/v1/api/areas").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/v1/api/areas/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.numero").value(40))
                .andExpect(jsonPath("$.nome").value("Área 40"))
                .andExpect(jsonPath("$.ativa").value(true));
    }

    @Test
    void criar_comCamposInvalidos_retorna400ENaoChamaService() throws Exception {
        String jsonInvalido = """
                { "numero": 0, "nome": "   " }
                """;

        mockMvc.perform(post("/v1/api/areas").contentType(MediaType.APPLICATION_JSON).content(jsonInvalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(areaService);
    }

    @Test
    void criar_semNumero_retorna400() throws Exception {
        mockMvc.perform(post("/v1/api/areas").contentType(MediaType.APPLICATION_JSON).content("""
                        { "nome": "Área 40" }
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(areaService);
    }

    @Test
    void criar_numeroDuplicado_retorna409ComProblemDetails() throws Exception {
        when(areaService.criar(any(AreaRequest.class))).thenThrow(new AreaNumeroDuplicadoException(40));

        mockMvc.perform(post("/v1/api/areas").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Já existe uma área com o número 40"));
    }

    @Test
    void listar_semFiltro_retornaLista() throws Exception {
        when(areaService.listar(null)).thenReturn(List.of(areaPersistida(UUID.randomUUID(), true)));

        mockMvc.perform(get("/v1/api/areas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].numero").value(40));
    }

    @Test
    void listar_comFiltroAtiva_repassaParametroAoService() throws Exception {
        when(areaService.listar(true)).thenReturn(List.of());

        mockMvc.perform(get("/v1/api/areas").param("ativa", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(areaService).listar(true);
    }

    @Test
    void buscarPorId_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(areaService.buscarPorId(id)).thenReturn(areaPersistida(id, true));

        mockMvc.perform(get("/v1/api/areas/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Área 40"));
    }

    @Test
    void buscarPorId_inexistente_retorna404ComProblemDetails() throws Exception {
        UUID id = UUID.randomUUID();
        when(areaService.buscarPorId(id)).thenThrow(new AreaNaoEncontradaException(id));

        mockMvc.perform(get("/v1/api/areas/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Área não encontrada: " + id));
    }

    @Test
    void buscarPorId_comIdMalFormado_retorna400() throws Exception {
        mockMvc.perform(get("/v1/api/areas/{id}", "nao-e-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void atualizar_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(areaService.atualizar(eq(id), any(AreaRequest.class))).thenReturn(areaPersistida(id, true));

        mockMvc.perform(put("/v1/api/areas/{id}", id).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void atualizar_numeroDuplicado_retorna409() throws Exception {
        UUID id = UUID.randomUUID();
        when(areaService.atualizar(eq(id), any(AreaRequest.class))).thenThrow(new AreaNumeroDuplicadoException(40));

        mockMvc.perform(put("/v1/api/areas/{id}", id).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict());
    }

    @Test
    void inativar_retorna204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/v1/api/areas/{id}", id))
                .andExpect(status().isNoContent());

        verify(areaService).inativar(id);
    }

    @Test
    void inativar_inexistente_retorna404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new AreaNaoEncontradaException(id)).when(areaService).inativar(id);

        mockMvc.perform(delete("/v1/api/areas/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void reativar_retorna200ComAreaAtiva() throws Exception {
        UUID id = UUID.randomUUID();
        when(areaService.reativar(id)).thenReturn(areaPersistida(id, true));

        mockMvc.perform(patch("/v1/api/areas/{id}/reativar", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativa").value(true));
    }
}
