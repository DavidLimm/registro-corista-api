package com.registraai.registro_coristas_api.pessoa;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class PessoaIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    private static final ZoneId RECIFE = ZoneId.of("America/Recife");

    // o banco é compartilhado pelos testes da classe; cada teste usa áreas próprias para não haver conflito
    private static final AtomicInteger PROXIMA_AREA = new AtomicInteger(1);

    // datas bem longe das fronteiras de idade, para o teste não depender do dia em que roda
    private static LocalDate haAnos(int anos, int meses) {
        return LocalDate.now(RECIFE).minusYears(anos).minusMonths(meses);
    }

    private static final String RESPONSAVEL = """
            , "responsavelLegalNome": "José da Silva", "responsavelLegalTelefone": "(81) 99999-0000",
              "consentimentoLgpd": true""";

    @Autowired
    private MockMvc mockMvc;

    private String idDoLocation(String location) {
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private String criarArea() throws Exception {
        int numero = PROXIMA_AREA.getAndIncrement();
        String location = mockMvc.perform(post("/v1/api/areas").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"numero\": %d, \"nome\": \"Área %d\" }".formatted(numero, numero)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return idDoLocation(location);
    }

    private String criarCongregacao(String areaId, String nome) throws Exception {
        String location = mockMvc.perform(post("/v1/api/congregacoes").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"areaId\": \"%s\", \"nome\": \"%s\" }".formatted(areaId, nome)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return idDoLocation(location);
    }

    private String corpo(String congregacaoId, String nome, LocalDate nascimento, String extra) {
        return """
                { "nome": "%s", "dataNascimento": "%s", "congregacaoId": "%s"%s }"""
                .formatted(nome, nascimento, congregacaoId, extra);
    }

    private ResultActions postPessoa(String corpo) throws Exception {
        return mockMvc.perform(post("/v1/api/pessoas").contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    /** Cadastra (com responsável e consentimento quando menor) e devolve o id da pessoa. */
    private String cadastrar(String congregacaoId, String nome, LocalDate nascimento) throws Exception {
        String extra = nascimento.isAfter(haAnos(18, 0)) ? RESPONSAVEL : "";
        String location = postPessoa(corpo(congregacaoId, nome, nascimento, extra))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return idDoLocation(location);
    }

    // ---------- cadastro ----------

    @Test
    void criarAdulto_entraPendenteComoJovemComEnderecoNormalizado() throws Exception {
        String congregacaoId = criarCongregacao(criarArea(), "Sede");
        String endereco = """
                , "endereco": { "logradouro": "Rua da Aurora", "bairro": "Boa Vista", "cidade": "Recife",
                                "uf": "pe", "cep": "50050-000" }""";

        String location = postPessoa(corpo(congregacaoId, "  Maria da Silva  ", haAnos(30, 0), endereco))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.nome").value("Maria da Silva"))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.idade").value(30))
                .andExpect(jsonPath("$.faixaEtaria").value("JOVEM"))
                .andExpect(jsonPath("$.congregacao.nome").value("Sede"))
                .andExpect(jsonPath("$.endereco.uf").value("PE"))
                .andExpect(jsonPath("$.endereco.cep").value("50050000"))
                .andExpect(jsonPath("$.aprovadoPor").doesNotExist())
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Maria da Silva"))
                .andExpect(jsonPath("$.congregacao.area.nome").exists())
                .andExpect(jsonPath("$.endereco.logradouro").value("Rua da Aurora"));
    }

    @Test
    void criarMenor_gravaResponsavelSoComDigitosEConsentimento() throws Exception {
        String congregacaoId = criarCongregacao(criarArea(), "Sede");

        postPessoa(corpo(congregacaoId, "Ana", haAnos(15, 6), RESPONSAVEL))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.faixaEtaria").value("ADOLESCENTE"))
                .andExpect(jsonPath("$.responsavelLegalNome").value("José da Silva"))
                .andExpect(jsonPath("$.responsavelLegalTelefone").value("81999990000"))
                .andExpect(jsonPath("$.consentimentoLgpdEm").exists());
    }

    @Test
    void criarMenor_semResponsavelOuSemConsentimento_retorna400() throws Exception {
        String congregacaoId = criarCongregacao(criarArea(), "Sede");

        postPessoa(corpo(congregacaoId, "Bia", haAnos(15, 0), ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("responsável legal")));
        postPessoa(corpo(congregacaoId, "Bia", haAnos(15, 0), """
                , "responsavelLegalNome": "José", "responsavelLegalTelefone": "81999990000" """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("consentimento")));
        postPessoa(corpo(congregacaoId, "Bia", haAnos(15, 0), ", \"consentimentoLgpd\": true"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_responsavelComSoUmDosCampos_retorna400() throws Exception {
        String congregacaoId = criarCongregacao(criarArea(), "Sede");

        postPessoa(corpo(congregacaoId, "Caio", haAnos(30, 0), ", \"responsavelLegalNome\": \"José\""))
                .andExpect(status().isBadRequest());
        postPessoa(corpo(congregacaoId, "Caio", haAnos(30, 0), ", \"responsavelLegalTelefone\": \"81999990000\""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_comDadosInvalidosOuCongregacaoIrregular() throws Exception {
        String areaId = criarArea();
        String sede = criarCongregacao(areaId, "Sede");
        String fechada = criarCongregacao(areaId, "Fechada");
        mockMvc.perform(delete("/v1/api/congregacoes/" + fechada)).andExpect(status().isNoContent());

        postPessoa("{}").andExpect(status().isBadRequest());
        postPessoa(corpo(sede, "  ", haAnos(30, 0), "")).andExpect(status().isBadRequest());
        postPessoa(corpo(sede, "Duda", LocalDate.now(RECIFE).plusDays(1), "")).andExpect(status().isBadRequest());
        postPessoa(corpo(fechada, "Duda", haAnos(30, 0), "")).andExpect(status().isConflict());
        postPessoa(corpo("00000000-0000-0000-0000-000000000000", "Duda", haAnos(30, 0), ""))
                .andExpect(status().isNotFound());
    }

    // ---------- edição ----------

    @Test
    void atualizar_alteraOsDadosEMantemStatusEConsentimento() throws Exception {
        String areaId = criarArea();
        String sede = criarCongregacao(areaId, "Sede");
        String filial = criarCongregacao(areaId, "Filial");
        String id = cadastrar(sede, "Ana", haAnos(15, 6));

        mockMvc.perform(put("/v1/api/pessoas/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(filial, "Ana Paula", haAnos(15, 6), RESPONSAVEL.replace("true", "false"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Ana Paula"))
                .andExpect(jsonPath("$.congregacao.nome").value("Filial"))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                // o consentimento já dado não se perde quando o cliente não o reenvia como true
                .andExpect(jsonPath("$.consentimentoLgpdEm").exists());

        mockMvc.perform(get("/v1/api/pessoas/" + id))
                .andExpect(jsonPath("$.congregacao.nome").value("Filial"));
    }

    @Test
    void atualizar_adultoQueVirariaMenorExigeResponsavelENadaEGravado() throws Exception {
        String sede = criarCongregacao(criarArea(), "Sede");
        String id = cadastrar(sede, "Caio", haAnos(30, 0));

        mockMvc.perform(put("/v1/api/pessoas/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(sede, "Caio", haAnos(15, 0), "")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/v1/api/pessoas/" + id))
                .andExpect(jsonPath("$.faixaEtaria").value("JOVEM"))
                .andExpect(jsonPath("$.idade").value(30));
    }

    @Test
    void atualizar_paraCongregacaoInativaRetorna409EInexistenteRetorna404() throws Exception {
        String areaId = criarArea();
        String sede = criarCongregacao(areaId, "Sede");
        String fechada = criarCongregacao(areaId, "Fechada");
        String id = cadastrar(sede, "Duda", haAnos(30, 0));
        mockMvc.perform(delete("/v1/api/congregacoes/" + fechada)).andExpect(status().isNoContent());

        mockMvc.perform(put("/v1/api/pessoas/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(fechada, "Duda", haAnos(30, 0), "")))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/v1/api/pessoas/00000000-0000-0000-0000-000000000000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(sede, "Duda", haAnos(30, 0), "")))
                .andExpect(status().isNotFound());
    }

    @Test
    void atualizar_enderecoEnviadoAtualizaOMesmoRegistroEAusenteMantem() throws Exception {
        String sede = criarCongregacao(criarArea(), "Sede");
        String endereco = """
                , "endereco": { "logradouro": "%s", "bairro": "Boa Vista", "cidade": "Recife", "uf": "PE" }""";
        String location = postPessoa(corpo(sede, "Eva", haAnos(30, 0), endereco.formatted("Rua Velha")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        String enderecoId = JsonPath.read(
                mockMvc.perform(get(location)).andReturn().getResponse().getContentAsString(), "$.endereco.id");

        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(sede, "Eva", haAnos(30, 0), endereco.formatted("Rua Nova"))))
                .andExpect(jsonPath("$.endereco.logradouro").value("Rua Nova"))
                .andExpect(jsonPath("$.endereco.id").value(enderecoId));

        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(sede, "Eva", haAnos(30, 0), "")))
                .andExpect(jsonPath("$.endereco.logradouro").value("Rua Nova"));
    }

    // ---------- inativação ----------

    @Test
    void inativar_fazSoftDeleteMantendoOCadastroConsultavel() throws Exception {
        String sede = criarCongregacao(criarArea(), "Sede");
        String id = cadastrar(sede, "Fabi", haAnos(30, 0));

        mockMvc.perform(delete("/v1/api/pessoas/" + id)).andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/api/pessoas/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INATIVO"));
        // idempotente
        mockMvc.perform(delete("/v1/api/pessoas/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/v1/api/pessoas/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    // ---------- listagem ----------

    @Test
    void listar_filtraPorAreaCongregacaoFaixaEtariaStatusENome() throws Exception {
        String areaA = criarArea();
        String areaB = criarArea();
        String sedeA = criarCongregacao(areaA, "Sede A");
        String filialA = criarCongregacao(areaA, "Filial A");
        String sedeB = criarCongregacao(areaB, "Sede B");
        cadastrar(sedeA, "Beatriz Souza", haAnos(30, 0));
        String adolescente = cadastrar(sedeA, "Alice Lima", haAnos(15, 6));
        cadastrar(filialA, "Carlos Souza", haAnos(25, 0));
        cadastrar(sedeB, "Daniela Rocha", haAnos(28, 0));
        mockMvc.perform(delete("/v1/api/pessoas/" + adolescente)).andExpect(status().isNoContent());

        // por área: só as 3 da área A, ordenadas por nome
        mockMvc.perform(get("/v1/api/pessoas").param("areaId", areaA))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].nome").value("Alice Lima"))
                .andExpect(jsonPath("$.content[1].nome").value("Beatriz Souza"))
                .andExpect(jsonPath("$.content[2].nome").value("Carlos Souza"));

        // por congregação
        mockMvc.perform(get("/v1/api/pessoas").param("congregacaoId", filialA))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Carlos Souza"));

        // por faixa etária real
        mockMvc.perform(get("/v1/api/pessoas").param("areaId", areaA).param("faixaEtaria", "ADOLESCENTE"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Alice Lima"));
        mockMvc.perform(get("/v1/api/pessoas").param("areaId", areaA).param("faixaEtaria", "JOVEM"))
                .andExpect(jsonPath("$.page.totalElements").value(2));

        // por status (a adolescente foi inativada; as demais seguem pendentes)
        mockMvc.perform(get("/v1/api/pessoas").param("areaId", areaA).param("status", "INATIVO"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Alice Lima"));
        mockMvc.perform(get("/v1/api/pessoas").param("areaId", areaA).param("status", "PENDENTE"))
                .andExpect(jsonPath("$.page.totalElements").value(2));

        // por trecho do nome, sem diferenciar maiúsculas
        mockMvc.perform(get("/v1/api/pessoas").param("areaId", areaA).param("nome", "SOUZA"))
                .andExpect(jsonPath("$.page.totalElements").value(2));

        // % e _ digitados não viram curinga
        mockMvc.perform(get("/v1/api/pessoas").param("areaId", areaA).param("nome", "%"))
                .andExpect(jsonPath("$.page.totalElements").value(0));
        mockMvc.perform(get("/v1/api/pessoas").param("areaId", areaA).param("nome", "_"))
                .andExpect(jsonPath("$.page.totalElements").value(0));

        // filtros combinados
        mockMvc.perform(get("/v1/api/pessoas").param("areaId", areaA).param("faixaEtaria", "JOVEM")
                        .param("nome", "carlos"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Carlos Souza"));
    }

    @Test
    void listar_faixaEtariaNaFronteiraDosDezoitoAnos() throws Exception {
        String areaId = criarArea();
        String sede = criarCongregacao(areaId, "Sede");
        LocalDate hoje = LocalDate.now(RECIFE);
        cadastrar(sede, "Faz 18 hoje", hoje.minusYears(18));
        cadastrar(sede, "Faz 18 amanha", hoje.minusYears(18).plusDays(1));

        mockMvc.perform(get("/v1/api/pessoas").param("areaId", areaId).param("faixaEtaria", "JOVEM"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Faz 18 hoje"))
                .andExpect(jsonPath("$.content[0].faixaEtaria").value("JOVEM"));
        mockMvc.perform(get("/v1/api/pessoas").param("areaId", areaId).param("faixaEtaria", "ADOLESCENTE"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Faz 18 amanha"))
                .andExpect(jsonPath("$.content[0].faixaEtaria").value("ADOLESCENTE"));
    }

    @Test
    void listar_paginaOrdenandoPorNome() throws Exception {
        String areaId = criarArea();
        String sede = criarCongregacao(areaId, "Sede");
        for (String nome : new String[]{"Elisa", "Bruna", "Davi", "Ana", "Caio"}) {
            cadastrar(sede, nome, haAnos(30, 0));
        }

        mockMvc.perform(get("/v1/api/pessoas").param("areaId", areaId).param("size", "2").param("page", "0"))
                .andExpect(jsonPath("$.page.totalElements").value(5))
                .andExpect(jsonPath("$.page.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].nome").value("Ana"))
                .andExpect(jsonPath("$.content[1].nome").value("Bruna"));
        mockMvc.perform(get("/v1/api/pessoas").param("areaId", areaId).param("size", "2").param("page", "2"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Elisa"));
    }

    @Test
    void coristaCadastradoAparecePeloEndpointDePessoa() throws Exception {
        String areaId = criarArea();
        String sede = criarCongregacao(areaId, "Sede");
        String corpoCorista = """
                { "pessoa": { "nome": "Gabi Corista", "dataNascimento": "%s", "congregacaoId": "%s" },
                  "tipoVoz": "SOPRANO", "tamanhoCamisa": "M" }""".formatted(haAnos(30, 0), sede);
        mockMvc.perform(post("/v1/api/coristas").contentType(MediaType.APPLICATION_JSON).content(corpoCorista))
                .andExpect(status().isCreated());

        // a pessoa é uma só: o corista é uma especialização dela, então ela também é listada como pessoa
        mockMvc.perform(get("/v1/api/pessoas").param("areaId", areaId))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Gabi Corista"));
    }

    @Test
    void buscarInexistente_retorna404() throws Exception {
        mockMvc.perform(get("/v1/api/pessoas/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
