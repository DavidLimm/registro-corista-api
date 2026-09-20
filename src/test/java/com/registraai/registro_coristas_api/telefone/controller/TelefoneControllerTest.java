package com.registraai.registro_coristas_api.telefone.controller;

import com.registraai.registro_coristas_api.pessoa.exception.PessoaNaoEncontradaException;
import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.telefone.dto.TelefoneRequest;
import com.registraai.registro_coristas_api.telefone.exception.TelefoneDuplicadoException;
import com.registraai.registro_coristas_api.telefone.exception.TelefoneNaoEncontradoException;
import com.registraai.registro_coristas_api.telefone.model.Telefone;
import com.registraai.registro_coristas_api.telefone.service.TelefoneService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TelefoneController.class)
class TelefoneControllerTest {

    private static final UUID PESSOA_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String BASE = "/v1/api/pessoas/" + PESSOA_ID + "/telefones";

    private static final String JSON_VALIDO = """
            { "numero": "(81) 99999-0000", "whatsapp": true, "principal": false }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TelefoneService telefoneService;

    private Telefone telefonePersistido(UUID id, String numero, boolean whatsapp, boolean principal) {
        Instant agora = Instant.parse("2026-09-20T12:00:00Z");
        Pessoa pessoa = Pessoa.builder().id(PESSOA_ID).build();
        return Telefone.builder().id(id).pessoa(pessoa).numero(numero).whatsapp(whatsapp).principal(principal)
                .criadoEm(agora).atualizadoEm(agora).build();
    }

    @Test
    void criar_retorna201ComLocationEPessoaId() throws Exception {
        UUID id = UUID.randomUUID();
        when(telefoneService.criar(eq(PESSOA_ID), any(TelefoneRequest.class)))
                .thenReturn(telefonePersistido(id, "81999990000", true, true));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost" + BASE + "/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.pessoaId").value(PESSOA_ID.toString()))
                .andExpect(jsonPath("$.numero").value("81999990000"))
                .andExpect(jsonPath("$.whatsapp").value(true))
                .andExpect(jsonPath("$.principal").value(true));
    }

    @Test
    void criar_repassaOsCamposDoCorpoAoService() throws Exception {
        when(telefoneService.criar(eq(PESSOA_ID), any(TelefoneRequest.class)))
                .thenReturn(telefonePersistido(UUID.randomUUID(), "81999990000", true, false));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isCreated());

        verify(telefoneService).criar(PESSOA_ID, new TelefoneRequest("(81) 99999-0000", true, false));
    }

    @Test
    void criar_semWhatsappENemPrincipal_eAceito() throws Exception {
        when(telefoneService.criar(eq(PESSOA_ID), any(TelefoneRequest.class)))
                .thenReturn(telefonePersistido(UUID.randomUUID(), "8133334444", false, false));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{ \"numero\": \"8133334444\" }"))
                .andExpect(status().isCreated());

        verify(telefoneService).criar(PESSOA_ID, new TelefoneRequest("8133334444", null, null));
    }

    @Test
    void criar_numeroInvalido_retorna400SemChamarOService() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{ \"numero\": \"12345\" }"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(telefoneService);
    }

    @Test
    void criar_numeroEmBrancoOuAusente_retorna400() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{ \"numero\": \"  \" }"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{ \"whatsapp\": true }"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(telefoneService);
    }

    @Test
    void criar_pessoaInexistente_retorna404() throws Exception {
        when(telefoneService.criar(eq(PESSOA_ID), any(TelefoneRequest.class)))
                .thenThrow(new PessoaNaoEncontradaException(PESSOA_ID));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void criar_numeroDuplicado_retorna409() throws Exception {
        when(telefoneService.criar(eq(PESSOA_ID), any(TelefoneRequest.class)))
                .thenThrow(new TelefoneDuplicadoException("81999990000"));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void criar_pessoaIdQueNaoEUuid_retorna400() throws Exception {
        mockMvc.perform(post("/v1/api/pessoas/abc/telefones").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON_VALIDO))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(telefoneService);
    }

    @Test
    void listar_retornaOsTelefonesNaOrdemDoService() throws Exception {
        when(telefoneService.listar(PESSOA_ID)).thenReturn(List.of(
                telefonePersistido(UUID.randomUUID(), "81999990000", true, true),
                telefonePersistido(UUID.randomUUID(), "8133334444", false, false)));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].numero").value("81999990000"))
                .andExpect(jsonPath("$[0].principal").value(true))
                .andExpect(jsonPath("$[1].numero").value("8133334444"))
                .andExpect(jsonPath("$[1].principal").value(false));
    }

    @Test
    void listar_pessoaSemTelefones_retornaListaVazia() throws Exception {
        when(telefoneService.listar(PESSOA_ID)).thenReturn(List.of());

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listar_pessoaInexistente_retorna404() throws Exception {
        when(telefoneService.listar(PESSOA_ID)).thenThrow(new PessoaNaoEncontradaException(PESSOA_ID));

        mockMvc.perform(get(BASE)).andExpect(status().isNotFound());
    }

    @Test
    void buscarPorId_retornaOTelefone() throws Exception {
        UUID id = UUID.randomUUID();
        when(telefoneService.buscarPorId(PESSOA_ID, id)).thenReturn(telefonePersistido(id, "8133334444", false, false));

        mockMvc.perform(get(BASE + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.numero").value("8133334444"));
    }

    @Test
    void buscarPorId_inexistente_retorna404() throws Exception {
        UUID id = UUID.randomUUID();
        when(telefoneService.buscarPorId(PESSOA_ID, id)).thenThrow(new TelefoneNaoEncontradoException(id));

        mockMvc.perform(get(BASE + "/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void atualizar_retornaOTelefoneAtualizado() throws Exception {
        UUID id = UUID.randomUUID();
        when(telefoneService.atualizar(eq(PESSOA_ID), eq(id), any(TelefoneRequest.class)))
                .thenReturn(telefonePersistido(id, "81999990000", true, true));

        mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.numero").value("81999990000"));
    }

    @Test
    void atualizar_corpoInvalido_retorna400SemChamarOService() throws Exception {
        mockMvc.perform(put(BASE + "/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"numero\": \"abc\" }"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(telefoneService);
    }

    @Test
    void atualizar_numeroDuplicado_retorna409() throws Exception {
        UUID id = UUID.randomUUID();
        when(telefoneService.atualizar(eq(PESSOA_ID), eq(id), any(TelefoneRequest.class)))
                .thenThrow(new TelefoneDuplicadoException("81999990000"));

        mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict());
    }

    @Test
    void remover_retorna204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent());

        verify(telefoneService).remover(PESSOA_ID, id);
    }

    @Test
    void remover_inexistente_retorna404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new TelefoneNaoEncontradoException(id)).when(telefoneService).remover(PESSOA_ID, id);

        mockMvc.perform(delete(BASE + "/" + id)).andExpect(status().isNotFound());
    }
}
