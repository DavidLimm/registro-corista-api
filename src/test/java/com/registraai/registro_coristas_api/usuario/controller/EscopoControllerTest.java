package com.registraai.registro_coristas_api.usuario.controller;

import com.registraai.registro_coristas_api.agrupamento.model.Agrupamento;
import com.registraai.registro_coristas_api.agrupamento.model.TipoAgrupamento;
import com.registraai.registro_coristas_api.autenticacao.service.UsuarioLogado;
import com.registraai.registro_coristas_api.suporte.SegurancaTesteConfig;
import com.registraai.registro_coristas_api.suporte.WithUsuarioTeste;
import com.registraai.registro_coristas_api.usuario.exception.EscopoInvalidoException;
import com.registraai.registro_coristas_api.usuario.service.EscopoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EscopoController.class)
@Import(SegurancaTesteConfig.class)
@WithUsuarioTeste
class EscopoControllerTest {

    private final UUID usuarioId = UUID.randomUUID();
    private final Agrupamento area40 = Agrupamento.builder().id(UUID.randomUUID()).tipo(TipoAgrupamento.AREA).numero(40).build();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EscopoService escopoService;

    @MockitoBean
    private UsuarioLogado usuarioLogado;

    @Test
    void escopoDe_devolveGlobalEOsAgrupamentos() throws Exception {
        when(escopoService.ehGlobal(usuarioId)).thenReturn(false);
        when(escopoService.agrupamentosDe(usuarioId)).thenReturn(List.of(area40));

        mockMvc.perform(get("/v1/api/usuarios/" + usuarioId + "/agrupamentos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(usuarioId.toString()))
                .andExpect(jsonPath("$.global").value(false))
                .andExpect(jsonPath("$.agrupamentos[0].numero").value(40));
    }

    @Test
    void definirEscopo_repassaOsIdsEDevolveOEscopo() throws Exception {
        when(escopoService.ehGlobal(usuarioId)).thenReturn(false);
        when(escopoService.agrupamentosDe(usuarioId)).thenReturn(List.of(area40));

        mockMvc.perform(put("/v1/api/usuarios/" + usuarioId + "/agrupamentos").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"agrupamentoIds\": [\"" + area40.getId() + "\"] }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agrupamentos[0].id").value(area40.getId().toString()));

        verify(escopoService).definir(usuarioId, Set.of(area40.getId()));
    }

    @Test
    void definirEscopo_semOCampoOuComIdInvalido_retorna400ENaoChamaService() throws Exception {
        mockMvc.perform(put("/v1/api/usuarios/" + usuarioId + "/agrupamentos").contentType(MediaType.APPLICATION_JSON)
                        .content("{ }"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/v1/api/usuarios/" + usuarioId + "/agrupamentos").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"agrupamentoIds\": [\"nao-e-uuid\"] }"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(escopoService);
    }

    @Test
    void definirEscopo_regraViolada_retorna409() throws Exception {
        when(escopoService.definir(usuarioId, Set.of()))
                .thenThrow(new EscopoInvalidoException("Este papel exige exatamente 1 agrupamento (informados: 0)"));

        mockMvc.perform(put("/v1/api/usuarios/" + usuarioId + "/agrupamentos").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"agrupamentoIds\": [] }"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Este papel exige exatamente 1 agrupamento (informados: 0)"));
    }

    @Test
    void meuEscopo_usaOUsuarioLogado() throws Exception {
        com.registraai.registro_coristas_api.usuario.model.Usuario logado = new com.registraai.registro_coristas_api.usuario.model.Usuario();
        logado.setId(usuarioId);
        when(usuarioLogado.atual()).thenReturn(java.util.Optional.of(logado));
        when(escopoService.ehGlobal(usuarioId)).thenReturn(false);
        when(escopoService.agrupamentosDe(usuarioId)).thenReturn(List.of(area40));

        mockMvc.perform(get("/v1/api/usuarios/meu/agrupamentos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(usuarioId.toString()))
                .andExpect(jsonPath("$.agrupamentos[0].numero").value(40));
    }
}
