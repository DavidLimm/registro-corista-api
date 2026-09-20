package com.registraai.registro_coristas_api.corista.controller;

import com.registraai.registro_coristas_api.area.model.Area;
import com.registraai.registro_coristas_api.congregacao.exception.CongregacaoInativaException;
import com.registraai.registro_coristas_api.congregacao.exception.CongregacaoNaoEncontradaException;
import com.registraai.registro_coristas_api.congregacao.model.Congregacao;
import com.registraai.registro_coristas_api.corista.dto.CoristaFiltro;
import com.registraai.registro_coristas_api.corista.dto.CoristaRequest;
import com.registraai.registro_coristas_api.corista.exception.CoristaNaoEncontradoException;
import com.registraai.registro_coristas_api.corista.model.Corista;
import com.registraai.registro_coristas_api.corista.model.ListaClassificacao;
import com.registraai.registro_coristas_api.corista.model.TamanhoCamisa;
import com.registraai.registro_coristas_api.corista.model.TipoVoz;
import com.registraai.registro_coristas_api.corista.service.CoristaService;
import com.registraai.registro_coristas_api.pessoa.exception.ConsentimentoLgpdObrigatorioException;
import com.registraai.registro_coristas_api.pessoa.exception.ResponsavelLegalObrigatorioException;
import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.pessoa.model.StatusPessoa;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

@WebMvcTest(CoristaController.class)
class CoristaControllerTest {

    /** Relógio fixo: hoje = 2026-09-20, para a idade e a faixa etária das respostas serem determinísticas. */
    @TestConfiguration
    static class RelogioFixo {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-09-20T15:00:00Z"), ZoneId.of("America/Recife"));
        }
    }

    private static final UUID CONGREGACAO_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID AREA_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final String JSON_VALIDO = """
            {
              "pessoa": {
                "nome": "Maria da Silva",
                "dataNascimento": "2000-05-17",
                "congregacaoId": "22222222-2222-2222-2222-222222222222",
                "endereco": { "logradouro": "Rua da Aurora", "bairro": "Boa Vista", "cidade": "Recife", "uf": "PE" }
              },
              "tipoVoz": "SOPRANO",
              "tamanhoCamisa": "M",
              "ocupacao": "Estudante"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CoristaService coristaService;

    private Corista coristaPersistido(UUID id, LocalDate nascimento, ListaClassificacao lista) {
        Instant agora = Instant.parse("2026-09-20T12:00:00Z");
        Area area = Area.builder().id(AREA_ID).numero(40).nome("Área 40").criadoEm(agora).atualizadoEm(agora).build();
        Congregacao congregacao = Congregacao.builder().id(CONGREGACAO_ID).area(area).nome("Sede").build();
        Pessoa pessoa = Pessoa.builder()
                .id(UUID.randomUUID()).nome("Maria da Silva").dataNascimento(nascimento)
                .status(StatusPessoa.PENDENTE).congregacao(congregacao)
                .criadoEm(agora).atualizadoEm(agora).build();
        return Corista.builder()
                .id(id).pessoa(pessoa).tipoVoz(TipoVoz.SOPRANO).tamanhoCamisa(TamanhoCamisa.M).ocupacao("Estudante")
                .listaClassificacao(lista).criadoEm(agora).atualizadoEm(agora).build();
    }

    // ---------- criar ----------

    @Test
    void criar_retorna201ComLocationEPessoaAninhadaComIdadeEFaixaEtaria() throws Exception {
        UUID id = UUID.randomUUID();
        when(coristaService.criar(any(CoristaRequest.class)))
                .thenReturn(coristaPersistido(id, LocalDate.of(2000, 5, 17), ListaClassificacao.JOVEM));

        mockMvc.perform(post("/v1/api/coristas").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/v1/api/coristas/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.listaClassificacao").value("JOVEM"))
                .andExpect(jsonPath("$.tipoVoz").value("SOPRANO"))
                .andExpect(jsonPath("$.tamanhoCamisa").value("M"))
                .andExpect(jsonPath("$.pessoa.nome").value("Maria da Silva"))
                .andExpect(jsonPath("$.pessoa.status").value("PENDENTE"))
                .andExpect(jsonPath("$.pessoa.idade").value(26))
                .andExpect(jsonPath("$.pessoa.faixaEtaria").value("JOVEM"))
                .andExpect(jsonPath("$.pessoa.congregacao.nome").value("Sede"))
                .andExpect(jsonPath("$.pessoa.congregacao.area.numero").value(40))
                .andExpect(jsonPath("$.pessoa.endereco").doesNotExist());
    }

    @Test
    void criar_semPessoa_retorna400ENaoChamaService() throws Exception {
        mockMvc.perform(post("/v1/api/coristas").contentType(MediaType.APPLICATION_JSON).content("""
                        { "tipoVoz": "SOPRANO" }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(coristaService);
    }

    @Test
    void criar_comDadosDaPessoaInvalidos_retorna400PorValidacaoAninhada() throws Exception {
        String invalido = """
                {
                  "pessoa": {
                    "nome": "  ",
                    "dataNascimento": "2999-01-01",
                    "responsavelLegalTelefone": "123"
                  }
                }
                """;

        mockMvc.perform(post("/v1/api/coristas").contentType(MediaType.APPLICATION_JSON).content(invalido))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(coristaService);
    }

    private static final String PESSOA_MINIMA = """
            "pessoa": { "nome": "Maria", "dataNascimento": "2000-05-17",
                        "congregacaoId": "22222222-2222-2222-2222-222222222222" }""";

    @Test
    void criar_semTipoVozOuSemTamanhoDeCamisa_retorna400ENaoChamaService() throws Exception {
        mockMvc.perform(post("/v1/api/coristas").contentType(MediaType.APPLICATION_JSON)
                        .content("{ " + PESSOA_MINIMA + ", \"tamanhoCamisa\": \"M\" }"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/v1/api/coristas").contentType(MediaType.APPLICATION_JSON)
                        .content("{ " + PESSOA_MINIMA + ", \"tipoVoz\": \"SOPRANO\" }"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(coristaService);
    }

    @Test
    void criar_comValorForaDosEnums_retorna400ENaoChamaService() throws Exception {
        mockMvc.perform(post("/v1/api/coristas").contentType(MediaType.APPLICATION_JSON)
                        .content("{ " + PESSOA_MINIMA + ", \"tipoVoz\": \"FALSETE\", \"tamanhoCamisa\": \"M\" }"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/v1/api/coristas").contentType(MediaType.APPLICATION_JSON)
                        .content("{ " + PESSOA_MINIMA + ", \"tipoVoz\": \"SOPRANO\", \"tamanhoCamisa\": \"XXL\" }"))
                .andExpect(status().isBadRequest());
        // o enum é sensível a maiúsculas: a API só aceita os nomes exatos
        mockMvc.perform(post("/v1/api/coristas").contentType(MediaType.APPLICATION_JSON)
                        .content("{ " + PESSOA_MINIMA + ", \"tipoVoz\": \"soprano\", \"tamanhoCamisa\": \"M\" }"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(coristaService);
    }

    @Test
    void criar_comOcupacaoMuitoLonga_retorna400() throws Exception {
        mockMvc.perform(post("/v1/api/coristas").contentType(MediaType.APPLICATION_JSON)
                        .content("{ " + PESSOA_MINIMA + ", \"tipoVoz\": \"SOPRANO\", \"tamanhoCamisa\": \"M\","
                                + " \"ocupacao\": \"" + "x".repeat(101) + "\" }"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(coristaService);
    }

    @Test
    void criar_aceitaTodosOsValoresDosEnums() throws Exception {
        UUID id = UUID.randomUUID();
        when(coristaService.criar(any(CoristaRequest.class)))
                .thenReturn(coristaPersistido(id, LocalDate.of(2000, 5, 17), ListaClassificacao.JOVEM));

        for (TipoVoz voz : TipoVoz.values()) {
            for (TamanhoCamisa camisa : TamanhoCamisa.values()) {
                mockMvc.perform(post("/v1/api/coristas").contentType(MediaType.APPLICATION_JSON)
                                .content("{ " + PESSOA_MINIMA + ", \"tipoVoz\": \"" + voz + "\", \"tamanhoCamisa\": \""
                                        + camisa + "\" }"))
                        .andExpect(status().isCreated());
            }
        }
    }

    @Test
    void criar_menorSemResponsavel_retorna400ComOrientacao() throws Exception {
        when(coristaService.criar(any(CoristaRequest.class))).thenThrow(new ResponsavelLegalObrigatorioException());

        mockMvc.perform(post("/v1/api/coristas").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Menores de 18 anos exigem responsável legal (nome e telefone)."));
    }

    @Test
    void criar_menorSemConsentimento_retorna400() throws Exception {
        when(coristaService.criar(any(CoristaRequest.class))).thenThrow(new ConsentimentoLgpdObrigatorioException());

        mockMvc.perform(post("/v1/api/coristas").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Menores de 18 anos exigem consentimento explícito (LGPD)."));
    }

    @Test
    void criar_congregacaoInexistente_retorna404() throws Exception {
        when(coristaService.criar(any(CoristaRequest.class)))
                .thenThrow(new CongregacaoNaoEncontradaException(CONGREGACAO_ID));

        mockMvc.perform(post("/v1/api/coristas").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isNotFound());
    }

    @Test
    void criar_congregacaoInativa_retorna409() throws Exception {
        when(coristaService.criar(any(CoristaRequest.class))).thenThrow(new CongregacaoInativaException("Sede"));

        mockMvc.perform(post("/v1/api/coristas").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict());
    }

    // ---------- listar ----------

    @Test
    void listar_repassaFiltrosEPaginacaoEDevolveOsMetadadosDaPagina() throws Exception {
        UUID id = UUID.randomUUID();
        var pagina = new PageImpl<>(
                List.of(coristaPersistido(id, LocalDate.of(2010, 3, 1), ListaClassificacao.ADOLESCENTE)),
                PageRequest.of(1, 5), 11);
        var filtro = new CoristaFiltro("mar", AREA_ID, CONGREGACAO_ID, ListaClassificacao.ADOLESCENTE, StatusPessoa.PENDENTE);
        when(coristaService.listar(filtro, 1, 5)).thenReturn(pagina);

        mockMvc.perform(get("/v1/api/coristas")
                        .param("nome", "mar")
                        .param("areaId", AREA_ID.toString())
                        .param("congregacaoId", CONGREGACAO_ID.toString())
                        .param("listaClassificacao", "ADOLESCENTE")
                        .param("status", "PENDENTE")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.content[0].pessoa.faixaEtaria").value("ADOLESCENTE"))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(5))
                .andExpect(jsonPath("$.page.totalElements").value(11))
                .andExpect(jsonPath("$.page.totalPages").value(3));
    }

    @Test
    void listar_semParametros_usaPaginaZeroTamanhoVinteESemFiltros() throws Exception {
        var semFiltro = new CoristaFiltro(null, null, null, null, null);
        when(coristaService.listar(semFiltro, 0, 20)).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/v1/api/coristas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        verify(coristaService).listar(semFiltro, 0, 20);
    }

    @Test
    void listar_comParametrosInvalidos_retorna400ENaoChamaService() throws Exception {
        mockMvc.perform(get("/v1/api/coristas").param("size", "101")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/api/coristas").param("size", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/api/coristas").param("page", "-1")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/api/coristas").param("listaClassificacao", "NAO_EXISTE")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/api/coristas").param("areaId", "nao-e-uuid")).andExpect(status().isBadRequest());

        verifyNoInteractions(coristaService);
    }

    // ---------- buscar / atualizar / inativar ----------

    @Test
    void buscarPorId_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(coristaService.buscarPorId(id))
                .thenReturn(coristaPersistido(id, LocalDate.of(2010, 3, 1), ListaClassificacao.ADOLESCENTE));

        mockMvc.perform(get("/v1/api/coristas/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pessoa.idade").value(16))
                .andExpect(jsonPath("$.pessoa.faixaEtaria").value("ADOLESCENTE"));
    }

    @Test
    void buscarPorId_inexistente_retorna404ComProblemDetails() throws Exception {
        UUID id = UUID.randomUUID();
        when(coristaService.buscarPorId(id)).thenThrow(new CoristaNaoEncontradoException(id));

        mockMvc.perform(get("/v1/api/coristas/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Corista não encontrado: " + id));
    }

    @Test
    void buscarPorId_comIdMalFormado_retorna400() throws Exception {
        mockMvc.perform(get("/v1/api/coristas/{id}", "nao-e-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void atualizar_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(coristaService.atualizar(eq(id), any(CoristaRequest.class)))
                .thenReturn(coristaPersistido(id, LocalDate.of(2000, 5, 17), ListaClassificacao.JOVEM));

        mockMvc.perform(put("/v1/api/coristas/{id}", id).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void atualizar_inexistente_retorna404() throws Exception {
        UUID id = UUID.randomUUID();
        when(coristaService.atualizar(eq(id), any(CoristaRequest.class))).thenThrow(new CoristaNaoEncontradoException(id));

        mockMvc.perform(put("/v1/api/coristas/{id}", id).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isNotFound());
    }

    @Test
    void atualizar_comCorpoInvalido_retorna400() throws Exception {
        mockMvc.perform(put("/v1/api/coristas/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(coristaService);
    }

    @Test
    void inativar_retorna204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/v1/api/coristas/{id}", id)).andExpect(status().isNoContent());

        verify(coristaService).inativar(id);
    }

    @Test
    void inativar_inexistente_retorna404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new CoristaNaoEncontradoException(id)).when(coristaService).inativar(id);

        mockMvc.perform(delete("/v1/api/coristas/{id}", id)).andExpect(status().isNotFound());
    }
}
