package com.registraai.registro_coristas_api.pessoa.controller;

import com.registraai.registro_coristas_api.area.model.Area;
import com.registraai.registro_coristas_api.congregacao.exception.CongregacaoInativaException;
import com.registraai.registro_coristas_api.congregacao.exception.CongregacaoNaoEncontradaException;
import com.registraai.registro_coristas_api.congregacao.model.Congregacao;
import com.registraai.registro_coristas_api.endereco.model.Endereco;
import com.registraai.registro_coristas_api.pessoa.dto.PessoaFiltro;
import com.registraai.registro_coristas_api.pessoa.dto.PessoaRequest;
import com.registraai.registro_coristas_api.pessoa.exception.ConsentimentoLgpdObrigatorioException;
import com.registraai.registro_coristas_api.pessoa.exception.PessoaNaoEncontradaException;
import com.registraai.registro_coristas_api.pessoa.exception.ResponsavelLegalIncompletoException;
import com.registraai.registro_coristas_api.pessoa.exception.ResponsavelLegalObrigatorioException;
import com.registraai.registro_coristas_api.pessoa.model.FaixaEtaria;
import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.pessoa.model.StatusPessoa;
import com.registraai.registro_coristas_api.pessoa.service.PessoaService;
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

@WebMvcTest(PessoaController.class)
class PessoaControllerTest {

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
              "nome": "Maria da Silva",
              "dataNascimento": "2000-05-17",
              "congregacaoId": "22222222-2222-2222-2222-222222222222",
              "endereco": { "logradouro": "Rua da Aurora", "bairro": "Boa Vista", "cidade": "Recife", "uf": "PE" }
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PessoaService pessoaService;

    private Pessoa pessoaPersistida(UUID id, LocalDate nascimento, StatusPessoa status, boolean comEndereco) {
        Instant agora = Instant.parse("2026-09-20T12:00:00Z");
        Area area = Area.builder().id(AREA_ID).numero(40).nome("Área 40").criadoEm(agora).atualizadoEm(agora).build();
        Congregacao congregacao = Congregacao.builder().id(CONGREGACAO_ID).area(area).nome("Sede").ativa(true).build();
        Endereco endereco = comEndereco
                ? Endereco.builder().id(UUID.randomUUID()).logradouro("Rua da Aurora").bairro("Boa Vista")
                        .cidade("Recife").uf("PE").criadoEm(agora).atualizadoEm(agora).build()
                : null;
        return Pessoa.builder()
                .id(id).nome("Maria da Silva").dataNascimento(nascimento).status(status)
                .congregacao(congregacao).endereco(endereco)
                .criadoEm(agora).atualizadoEm(agora).build();
    }

    private Pessoa pessoaPersistida(UUID id, LocalDate nascimento) {
        return pessoaPersistida(id, nascimento, StatusPessoa.PENDENTE, false);
    }

    // ---------- criar ----------

    @Test
    void criar_retorna201ComLocationIdadeEFaixaEtaria() throws Exception {
        UUID id = UUID.randomUUID();
        when(pessoaService.criar(any(PessoaRequest.class)))
                .thenReturn(pessoaPersistida(id, LocalDate.of(2000, 5, 17), StatusPessoa.PENDENTE, true));

        mockMvc.perform(post("/v1/api/pessoas").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/v1/api/pessoas/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.nome").value("Maria da Silva"))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.idade").value(26))
                .andExpect(jsonPath("$.faixaEtaria").value("JOVEM"))
                .andExpect(jsonPath("$.congregacao.nome").value("Sede"))
                .andExpect(jsonPath("$.congregacao.area.numero").value(40))
                .andExpect(jsonPath("$.endereco.logradouro").value("Rua da Aurora"));
    }

    @Test
    void criar_semEndereco_retornaEnderecoNulo() throws Exception {
        when(pessoaService.criar(any(PessoaRequest.class)))
                .thenReturn(pessoaPersistida(UUID.randomUUID(), LocalDate.of(2000, 5, 17)));

        mockMvc.perform(post("/v1/api/pessoas").contentType(MediaType.APPLICATION_JSON).content("""
                        { "nome": "Maria", "dataNascimento": "2000-05-17",
                          "congregacaoId": "22222222-2222-2222-2222-222222222222" }"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.endereco").doesNotExist());
    }

    @Test
    void criar_repassaOsCamposDoCorpoAoService() throws Exception {
        when(pessoaService.criar(any(PessoaRequest.class)))
                .thenReturn(pessoaPersistida(UUID.randomUUID(), LocalDate.of(2010, 3, 1)));

        mockMvc.perform(post("/v1/api/pessoas").contentType(MediaType.APPLICATION_JSON).content("""
                        { "nome": "Ana", "dataNascimento": "2010-03-01",
                          "congregacaoId": "22222222-2222-2222-2222-222222222222",
                          "responsavelLegalNome": "José", "responsavelLegalTelefone": "(81) 99999-0000",
                          "consentimentoLgpd": true }"""))
                .andExpect(status().isCreated());

        verify(pessoaService).criar(new PessoaRequest("Ana", LocalDate.of(2010, 3, 1), CONGREGACAO_ID, null,
                "José", "(81) 99999-0000", true));
    }

    @Test
    void criar_comDadosInvalidos_retorna400ENaoChamaService() throws Exception {
        String[] invalidos = {
                "{}",
                // nome em branco
                """
                { "nome": "  ", "dataNascimento": "2000-05-17", "congregacaoId": "22222222-2222-2222-2222-222222222222" }""",
                // nascimento no futuro
                """
                { "nome": "Maria", "dataNascimento": "2999-01-01", "congregacaoId": "22222222-2222-2222-2222-222222222222" }""",
                // sem congregação
                """
                { "nome": "Maria", "dataNascimento": "2000-05-17" }""",
                // telefone do responsável fora do formato
                """
                { "nome": "Maria", "dataNascimento": "2000-05-17", "congregacaoId": "22222222-2222-2222-2222-222222222222",
                  "responsavelLegalTelefone": "123" }""",
                // endereço incompleto (validação aninhada)
                """
                { "nome": "Maria", "dataNascimento": "2000-05-17", "congregacaoId": "22222222-2222-2222-2222-222222222222",
                  "endereco": { "logradouro": "Rua" } }""",
                // nome longo demais
                """
                { "nome": "%s", "dataNascimento": "2000-05-17", "congregacaoId": "22222222-2222-2222-2222-222222222222" }"""
                        .formatted("a".repeat(151)),
        };

        for (String corpo : invalidos) {
            mockMvc.perform(post("/v1/api/pessoas").contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(pessoaService);
    }

    @Test
    void criar_menorSemResponsavel_retorna400ComOrientacao() throws Exception {
        when(pessoaService.criar(any(PessoaRequest.class))).thenThrow(new ResponsavelLegalObrigatorioException());

        mockMvc.perform(post("/v1/api/pessoas").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Menores de 18 anos exigem responsável legal (nome e telefone)."));
    }

    @Test
    void criar_menorSemConsentimento_retorna400() throws Exception {
        when(pessoaService.criar(any(PessoaRequest.class))).thenThrow(new ConsentimentoLgpdObrigatorioException());

        mockMvc.perform(post("/v1/api/pessoas").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Menores de 18 anos exigem consentimento explícito (LGPD)."));
    }

    @Test
    void criar_responsavelComSoUmDosCampos_retorna400() throws Exception {
        when(pessoaService.criar(any(PessoaRequest.class))).thenThrow(new ResponsavelLegalIncompletoException());

        mockMvc.perform(post("/v1/api/pessoas").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_congregacaoInexistente_retorna404() throws Exception {
        when(pessoaService.criar(any(PessoaRequest.class)))
                .thenThrow(new CongregacaoNaoEncontradaException(CONGREGACAO_ID));

        mockMvc.perform(post("/v1/api/pessoas").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isNotFound());
    }

    @Test
    void criar_congregacaoInativa_retorna409() throws Exception {
        when(pessoaService.criar(any(PessoaRequest.class))).thenThrow(new CongregacaoInativaException("Sede"));

        mockMvc.perform(post("/v1/api/pessoas").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict());
    }

    // ---------- listar ----------

    @Test
    void listar_repassaFiltrosEPaginacaoEDevolveOsMetadadosDaPagina() throws Exception {
        UUID id = UUID.randomUUID();
        var pagina = new PageImpl<>(List.of(pessoaPersistida(id, LocalDate.of(2010, 3, 1))),
                PageRequest.of(1, 5), 11);
        var filtro = new PessoaFiltro("mar", AREA_ID, CONGREGACAO_ID, FaixaEtaria.ADOLESCENTE, StatusPessoa.PENDENTE);
        when(pessoaService.listar(filtro, 1, 5)).thenReturn(pagina);

        mockMvc.perform(get("/v1/api/pessoas")
                        .param("nome", "mar")
                        .param("areaId", AREA_ID.toString())
                        .param("congregacaoId", CONGREGACAO_ID.toString())
                        .param("faixaEtaria", "ADOLESCENTE")
                        .param("status", "PENDENTE")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.content[0].faixaEtaria").value("ADOLESCENTE"))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(5))
                .andExpect(jsonPath("$.page.totalElements").value(11))
                .andExpect(jsonPath("$.page.totalPages").value(3));
    }

    @Test
    void listar_semParametros_usaPaginaZeroTamanhoVinteESemFiltros() throws Exception {
        var semFiltro = new PessoaFiltro(null, null, null, null, null);
        when(pessoaService.listar(semFiltro, 0, 20)).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/v1/api/pessoas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        verify(pessoaService).listar(semFiltro, 0, 20);
    }

    @Test
    void listar_comParametrosInvalidos_retorna400ENaoChamaService() throws Exception {
        mockMvc.perform(get("/v1/api/pessoas").param("size", "101")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/api/pessoas").param("size", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/api/pessoas").param("page", "-1")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/api/pessoas").param("faixaEtaria", "NAO_EXISTE")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/api/pessoas").param("status", "NAO_EXISTE")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/api/pessoas").param("areaId", "nao-e-uuid")).andExpect(status().isBadRequest());

        verifyNoInteractions(pessoaService);
    }

    // ---------- buscar / atualizar / inativar ----------

    @Test
    void buscarPorId_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(pessoaService.buscarPorId(id)).thenReturn(pessoaPersistida(id, LocalDate.of(2010, 3, 1)));

        mockMvc.perform(get("/v1/api/pessoas/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idade").value(16))
                .andExpect(jsonPath("$.faixaEtaria").value("ADOLESCENTE"));
    }

    @Test
    void buscarPorId_inexistente_retorna404ComProblemDetails() throws Exception {
        UUID id = UUID.randomUUID();
        when(pessoaService.buscarPorId(id)).thenThrow(new PessoaNaoEncontradaException(id));

        mockMvc.perform(get("/v1/api/pessoas/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Pessoa não encontrada: " + id));
    }

    @Test
    void buscarPorId_comIdMalFormado_retorna400() throws Exception {
        mockMvc.perform(get("/v1/api/pessoas/{id}", "nao-e-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void atualizar_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(pessoaService.atualizar(eq(id), any(PessoaRequest.class)))
                .thenReturn(pessoaPersistida(id, LocalDate.of(2000, 5, 17)));

        mockMvc.perform(put("/v1/api/pessoas/{id}", id).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void atualizar_inexistente_retorna404() throws Exception {
        UUID id = UUID.randomUUID();
        when(pessoaService.atualizar(eq(id), any(PessoaRequest.class))).thenThrow(new PessoaNaoEncontradaException(id));

        mockMvc.perform(put("/v1/api/pessoas/{id}", id).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isNotFound());
    }

    @Test
    void atualizar_comCorpoInvalido_retorna400() throws Exception {
        mockMvc.perform(put("/v1/api/pessoas/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(pessoaService);
    }

    @Test
    void inativar_retorna204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/v1/api/pessoas/{id}", id)).andExpect(status().isNoContent());

        verify(pessoaService).inativar(id);
    }

    @Test
    void inativar_inexistente_retorna404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new PessoaNaoEncontradaException(id)).when(pessoaService).inativar(id);

        mockMvc.perform(delete("/v1/api/pessoas/{id}", id)).andExpect(status().isNotFound());
    }
}
