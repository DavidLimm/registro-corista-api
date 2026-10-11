package com.registraai.registro_coristas_api.congregacao.controller;

import com.registraai.registro_coristas_api.suporte.WithUsuarioTeste;
import com.registraai.registro_coristas_api.suporte.SegurancaTesteConfig;
import org.springframework.context.annotation.Import;
import com.registraai.registro_coristas_api.agrupamento.exception.SubdivisaoInativaException;
import com.registraai.registro_coristas_api.agrupamento.exception.SubdivisaoNaoEncontradaException;
import com.registraai.registro_coristas_api.agrupamento.model.Subdivisao;
import com.registraai.registro_coristas_api.suporte.HierarquiaTeste;
import com.registraai.registro_coristas_api.congregacao.dto.CongregacaoRequest;
import com.registraai.registro_coristas_api.congregacao.exception.CongregacaoNaoEncontradaException;
import com.registraai.registro_coristas_api.congregacao.exception.CongregacaoNomeDuplicadoException;
import com.registraai.registro_coristas_api.congregacao.model.Congregacao;
import com.registraai.registro_coristas_api.congregacao.service.CongregacaoService;
import com.registraai.registro_coristas_api.endereco.model.Endereco;
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

@WebMvcTest(CongregacaoController.class)
@Import(SegurancaTesteConfig.class)
@WithUsuarioTeste
class CongregacaoControllerTest {

    private static final UUID SUBDIVISAO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID AGRUPAMENTO_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final String JSON_VALIDO = """
            {
              "subdivisaoId": "11111111-1111-1111-1111-111111111111",
              "nome": "Sede",
              "endereco": {
                "logradouro": "Rua da Aurora", "numero": "123", "bairro": "Boa Vista",
                "cidade": "Recife", "uf": "PE", "cep": "50050-000"
              }
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CongregacaoService congregacaoService;

    private Congregacao congregacaoPersistida(UUID id, boolean ativa, boolean comEndereco) {
        Instant agora = Instant.parse("2026-09-20T12:00:00Z");
        Subdivisao subdivisao = HierarquiaTeste.subdivisaoDaArea40(AGRUPAMENTO_ID, SUBDIVISAO_ID);
        Endereco endereco = comEndereco
                ? Endereco.builder().id(UUID.randomUUID()).logradouro("Rua da Aurora").numero("123")
                        .bairro("Boa Vista").cidade("Recife").uf("PE").cep("50050000")
                        .criadoEm(agora).atualizadoEm(agora).build()
                : null;
        return Congregacao.builder()
                .id(id).subdivisao(subdivisao).endereco(endereco).nome("Sede").ativa(ativa)
                .criadoEm(agora).atualizadoEm(agora).build();
    }

    @Test
    void criar_retorna201ComLocationESubdivisaoAgrupamentoEEnderecoAninhados() throws Exception {
        UUID id = UUID.randomUUID();
        when(congregacaoService.criar(any(CongregacaoRequest.class))).thenReturn(congregacaoPersistida(id, true, true));

        mockMvc.perform(post("/v1/api/congregacoes").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/v1/api/congregacoes/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.nome").value("Sede"))
                .andExpect(jsonPath("$.ativa").value(true))
                .andExpect(jsonPath("$.subdivisao.id").value(SUBDIVISAO_ID.toString()))
                .andExpect(jsonPath("$.agrupamento.numero").value(40))
                .andExpect(jsonPath("$.endereco.cep").value("50050000"));
    }

    @Test
    void criar_semEndereco_retornaEnderecoNulo() throws Exception {
        UUID id = UUID.randomUUID();
        when(congregacaoService.criar(any(CongregacaoRequest.class))).thenReturn(congregacaoPersistida(id, true, false));

        mockMvc.perform(post("/v1/api/congregacoes").contentType(MediaType.APPLICATION_JSON).content("""
                        { "subdivisaoId": "11111111-1111-1111-1111-111111111111", "nome": "Sede" }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.endereco").doesNotExist());
    }

    @Test
    void criar_semSubdivisaoOuNomeEmBranco_retorna400ENaoChamaService() throws Exception {
        mockMvc.perform(post("/v1/api/congregacoes").contentType(MediaType.APPLICATION_JSON).content("""
                        { "nome": "   " }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(congregacaoService);
    }

    @Test
    void criar_comEnderecoInvalido_retorna400PorValidacaoAninhada() throws Exception {
        mockMvc.perform(post("/v1/api/congregacoes").contentType(MediaType.APPLICATION_JSON).content("""
                        {
                          "subdivisaoId": "11111111-1111-1111-1111-111111111111",
                          "nome": "Sede",
                          "endereco": { "logradouro": "", "bairro": "Boa Vista", "cidade": "Recife", "uf": "PER" }
                        }
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(congregacaoService);
    }

    @Test
    void criar_subdivisaoInexistente_retorna404() throws Exception {
        when(congregacaoService.criar(any(CongregacaoRequest.class))).thenThrow(new SubdivisaoNaoEncontradaException(SUBDIVISAO_ID));

        mockMvc.perform(post("/v1/api/congregacoes").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isNotFound());
    }

    @Test
    void criar_subdivisaoInativa_retorna409() throws Exception {
        when(congregacaoService.criar(any(CongregacaoRequest.class))).thenThrow(new SubdivisaoInativaException());

        mockMvc.perform(post("/v1/api/congregacoes").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A subdivisão (ou o agrupamento a que pertence) está inativa. Reative-a ou escolha outra."));
    }

    @Test
    void criar_nomeDuplicadoNaSubdivisao_retorna409() throws Exception {
        when(congregacaoService.criar(any(CongregacaoRequest.class)))
                .thenThrow(new CongregacaoNomeDuplicadoException("Sede"));

        mockMvc.perform(post("/v1/api/congregacoes").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Já existe uma congregação chamada \"Sede\" nesta subdivisão"));
    }

    @Test
    void listar_repassaFiltrosAoService() throws Exception {
        when(congregacaoService.listar(null, SUBDIVISAO_ID, true))
                .thenReturn(List.of(congregacaoPersistida(UUID.randomUUID(), true, false)));

        mockMvc.perform(get("/v1/api/congregacoes").param("subdivisaoId", SUBDIVISAO_ID.toString()).param("ativa", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Sede"));

        verify(congregacaoService).listar(null, SUBDIVISAO_ID, true);
    }

    @Test
    void listar_semFiltros_passaNulos() throws Exception {
        when(congregacaoService.listar(null, null, null)).thenReturn(List.of());

        mockMvc.perform(get("/v1/api/congregacoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listar_comSubdivisaoIdMalFormado_retorna400() throws Exception {
        mockMvc.perform(get("/v1/api/congregacoes").param("subdivisaoId", "nao-e-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(congregacaoService);
    }

    @Test
    void buscarPorId_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(congregacaoService.buscarPorId(id)).thenReturn(congregacaoPersistida(id, true, true));

        mockMvc.perform(get("/v1/api/congregacoes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Sede"))
                .andExpect(jsonPath("$.endereco.logradouro").value("Rua da Aurora"));
    }

    @Test
    void buscarPorId_inexistente_retorna404ComProblemDetails() throws Exception {
        UUID id = UUID.randomUUID();
        when(congregacaoService.buscarPorId(id)).thenThrow(new CongregacaoNaoEncontradaException(id));

        mockMvc.perform(get("/v1/api/congregacoes/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Congregação não encontrada: " + id));
    }

    @Test
    void buscarPorId_comIdMalFormado_retorna400() throws Exception {
        mockMvc.perform(get("/v1/api/congregacoes/{id}", "nao-e-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void atualizar_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(congregacaoService.atualizar(eq(id), any(CongregacaoRequest.class)))
                .thenReturn(congregacaoPersistida(id, true, true));

        mockMvc.perform(put("/v1/api/congregacoes/{id}", id).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void atualizar_destinoInativoOuNomeDuplicado_retorna409() throws Exception {
        UUID id = UUID.randomUUID();
        when(congregacaoService.atualizar(eq(id), any(CongregacaoRequest.class)))
                .thenThrow(new SubdivisaoInativaException());

        mockMvc.perform(put("/v1/api/congregacoes/{id}", id).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict());
    }

    @Test
    void atualizar_inexistente_retorna404() throws Exception {
        UUID id = UUID.randomUUID();
        when(congregacaoService.atualizar(eq(id), any(CongregacaoRequest.class)))
                .thenThrow(new CongregacaoNaoEncontradaException(id));

        mockMvc.perform(put("/v1/api/congregacoes/{id}", id).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isNotFound());
    }

    @Test
    void inativar_retorna204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/v1/api/congregacoes/{id}", id))
                .andExpect(status().isNoContent());

        verify(congregacaoService).inativar(id);
    }

    @Test
    void inativar_inexistente_retorna404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new CongregacaoNaoEncontradaException(id)).when(congregacaoService).inativar(id);

        mockMvc.perform(delete("/v1/api/congregacoes/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void reativar_retorna200ComCongregacaoAtiva() throws Exception {
        UUID id = UUID.randomUUID();
        when(congregacaoService.reativar(id)).thenReturn(congregacaoPersistida(id, true, false));

        mockMvc.perform(patch("/v1/api/congregacoes/{id}/reativar", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativa").value(true));
    }

    @Test
    void reativar_comSubdivisaoInativa_retorna409() throws Exception {
        UUID id = UUID.randomUUID();
        when(congregacaoService.reativar(id)).thenThrow(new SubdivisaoInativaException());

        mockMvc.perform(patch("/v1/api/congregacoes/{id}/reativar", id))
                .andExpect(status().isConflict());
    }
}
