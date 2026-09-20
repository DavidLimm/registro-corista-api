package com.registraai.registro_coristas_api.corista;

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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fluxo completo Controller → Service → Repository → Postgres real (Flyway aplica o schema). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CoristaIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    private static final ZoneId RECIFE = ZoneId.of("America/Recife");

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

    private String criarArea(int numero) throws Exception {
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

    // voz e camisa são obrigatórias; quando o teste não se importa com elas, usa este par válido
    private static final String CAMPOS_PADRAO_DE_CORISTA = ", \"tipoVoz\": \"SOPRANO\", \"tamanhoCamisa\": \"M\"";

    /** {@code camposDeCorista} vazio usa {@link #CAMPOS_PADRAO_DE_CORISTA}; quando informado, vale exatamente o informado. */
    private String corpo(String congregacaoId, String nome, LocalDate nascimento, String extraPessoa, String camposDeCorista) {
        String camposDoCorista = camposDeCorista.isEmpty() ? CAMPOS_PADRAO_DE_CORISTA : camposDeCorista;
        return """
                { "pessoa": { "nome": "%s", "dataNascimento": "%s", "congregacaoId": "%s"%s }%s }"""
                .formatted(nome, nascimento, congregacaoId, extraPessoa, camposDoCorista);
    }

    private ResultActions postCorista(String corpo) throws Exception {
        return mockMvc.perform(post("/v1/api/coristas").contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    /** Cadastra e devolve o id do corista. */
    private String cadastrar(String congregacaoId, String nome, LocalDate nascimento) throws Exception {
        String extraPessoa = nascimento.isAfter(haAnos(18, 0)) ? RESPONSAVEL : "";
        String location = postCorista(corpo(congregacaoId, nome, nascimento, extraPessoa, ""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return idDoLocation(location);
    }

    // ---------- cadastro ----------

    @Test
    void criarJovem_entraPendenteNaListaDeJovensComEnderecoNormalizado() throws Exception {
        String congregacaoId = criarCongregacao(criarArea(1), "Sede");
        String endereco = """
                , "endereco": { "logradouro": "Rua da Aurora", "bairro": "Boa Vista", "cidade": "Recife",
                                "uf": "pe", "cep": "50050-000" }""";

        String location = postCorista(corpo(congregacaoId, "  Maria da Silva  ", haAnos(30, 0), endereco,
                ", \"tipoVoz\": \"SOPRANO\", \"tamanhoCamisa\": \"M\", \"ocupacao\": \"  \""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.listaClassificacao").value("JOVEM"))
                .andExpect(jsonPath("$.tipoVoz").value("SOPRANO"))
                .andExpect(jsonPath("$.ocupacao").doesNotExist())
                .andExpect(jsonPath("$.promovidoPor").doesNotExist())
                .andExpect(jsonPath("$.pessoa.nome").value("Maria da Silva"))
                .andExpect(jsonPath("$.pessoa.status").value("PENDENTE"))
                .andExpect(jsonPath("$.pessoa.idade").value(30))
                .andExpect(jsonPath("$.pessoa.faixaEtaria").value("JOVEM"))
                .andExpect(jsonPath("$.pessoa.aprovadoPor").doesNotExist())
                .andExpect(jsonPath("$.pessoa.congregacao.id").value(congregacaoId))
                .andExpect(jsonPath("$.pessoa.congregacao.area.numero").value(1))
                .andExpect(jsonPath("$.pessoa.endereco.uf").value("PE"))
                .andExpect(jsonPath("$.pessoa.endereco.cep").value("50050000"))
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pessoa.endereco.logradouro").value("Rua da Aurora"))
                .andExpect(jsonPath("$.pessoa.congregacao.nome").value("Sede"));
    }

    @Test
    void criarMenor_entraNaListaDeAdolescentesGravandoResponsavelEConsentimento() throws Exception {
        String congregacaoId = criarCongregacao(criarArea(2), "Sede");

        postCorista(corpo(congregacaoId, "Ana", haAnos(15, 6), RESPONSAVEL, ""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.listaClassificacao").value("ADOLESCENTE"))
                .andExpect(jsonPath("$.pessoa.faixaEtaria").value("ADOLESCENTE"))
                .andExpect(jsonPath("$.pessoa.idade").value(15))
                .andExpect(jsonPath("$.pessoa.responsavelLegalNome").value("José da Silva"))
                .andExpect(jsonPath("$.pessoa.responsavelLegalTelefone").value("81999990000"))
                .andExpect(jsonPath("$.pessoa.consentimentoLgpdEm").exists());
    }

    @Test
    void criarMenor_semResponsavelOuSemConsentimento_retorna400() throws Exception {
        String congregacaoId = criarCongregacao(criarArea(3), "Sede");

        postCorista(corpo(congregacaoId, "Ana", haAnos(15, 6), "", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("responsável legal")));

        postCorista(corpo(congregacaoId, "Ana", haAnos(15, 6),
                ", \"responsavelLegalNome\": \"José\", \"responsavelLegalTelefone\": \"81999990000\"", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("consentimento")));

        postCorista(corpo(congregacaoId, "Ana", haAnos(15, 6),
                ", \"responsavelLegalNome\": \"José\", \"responsavelLegalTelefone\": \"81999990000\","
                        + " \"consentimentoLgpd\": false", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_responsavelComSoUmDosCampos_retorna400() throws Exception {
        String congregacaoId = criarCongregacao(criarArea(4), "Sede");

        postCorista(corpo(congregacaoId, "Maria", haAnos(30, 0), ", \"responsavelLegalNome\": \"José\"", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("nome e telefone")));
    }

    @Test
    void criar_semVozOuCamisaOuComValorInexistente_retorna400() throws Exception {
        String congregacaoId = criarCongregacao(criarArea(15), "Sede");

        postCorista(corpo(congregacaoId, "Maria", haAnos(30, 0), "", ", \"tamanhoCamisa\": \"M\""))
                .andExpect(status().isBadRequest());
        postCorista(corpo(congregacaoId, "Maria", haAnos(30, 0), "", ", \"tipoVoz\": \"SOPRANO\""))
                .andExpect(status().isBadRequest());
        postCorista(corpo(congregacaoId, "Maria", haAnos(30, 0), "",
                ", \"tipoVoz\": \"FALSETE\", \"tamanhoCamisa\": \"M\"")).andExpect(status().isBadRequest());
        postCorista(corpo(congregacaoId, "Maria", haAnos(30, 0), "",
                ", \"tipoVoz\": \"SOPRANO\", \"tamanhoCamisa\": \"XXL\"")).andExpect(status().isBadRequest());
    }

    @Test
    void criar_todosOsValoresDeVozECamisaChegamAoBancoEVoltamNaResposta() throws Exception {
        String congregacaoId = criarCongregacao(criarArea(16), "Sede");
        String[] vozes = {"BAIXO", "CONTRALTO", "SOPRANO", "TENOR"};
        String[] camisas = {"P", "M", "G", "GG", "XGG"};

        for (int i = 0; i < camisas.length; i++) {
            String voz = vozes[i % vozes.length];
            String camisa = camisas[i];
            String location = postCorista(corpo(congregacaoId, "Corista " + i, haAnos(30, 0), "",
                    ", \"tipoVoz\": \"" + voz + "\", \"tamanhoCamisa\": \"" + camisa + "\""))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getHeader("Location");

            mockMvc.perform(get(location))
                    .andExpect(jsonPath("$.tipoVoz").value(voz))
                    .andExpect(jsonPath("$.tamanhoCamisa").value(camisa));
        }
    }

    @Test
    void criar_comDadosInvalidosOuCongregacaoIrregular() throws Exception {
        String areaId = criarArea(5);
        String congregacaoId = criarCongregacao(areaId, "Sede");

        // validação de formato
        postCorista(corpo(congregacaoId, " ", haAnos(30, 0), "", "")).andExpect(status().isBadRequest());
        postCorista(corpo(congregacaoId, "Maria", LocalDate.now(RECIFE).plusDays(1), "", ""))
                .andExpect(status().isBadRequest());
        postCorista(corpo(congregacaoId, "Maria", haAnos(30, 0), ", \"responsavelLegalTelefone\": \"123\"", ""))
                .andExpect(status().isBadRequest());
        postCorista("{ \"tipoVoz\": \"SOPRANO\" }").andExpect(status().isBadRequest());

        // congregação inexistente
        postCorista(corpo("00000000-0000-0000-0000-000000000000", "Maria", haAnos(30, 0), "", ""))
                .andExpect(status().isNotFound());

        // congregação inativa
        mockMvc.perform(delete("/v1/api/congregacoes/" + congregacaoId)).andExpect(status().isNoContent());
        postCorista(corpo(congregacaoId, "Maria", haAnos(30, 0), "", ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("inativa")));
    }

    // ---------- edição ----------

    @Test
    void atualizar_alteraPessoaECoristaEMantemStatusEConsentimento() throws Exception {
        String areaId = criarArea(6);
        String sede = criarCongregacao(areaId, "Sede");
        String filial = criarCongregacao(areaId, "Filial");
        String id = cadastrar(sede, "Ana", haAnos(15, 6));

        mockMvc.perform(put("/v1/api/coristas/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(filial, "Ana Paula", haAnos(15, 6), RESPONSAVEL.replace("true", "false"),
                                ", \"tipoVoz\": \"CONTRALTO\", \"tamanhoCamisa\": \"G\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pessoa.nome").value("Ana Paula"))
                .andExpect(jsonPath("$.pessoa.congregacao.nome").value("Filial"))
                .andExpect(jsonPath("$.pessoa.status").value("PENDENTE"))
                .andExpect(jsonPath("$.tipoVoz").value("CONTRALTO"))
                .andExpect(jsonPath("$.tamanhoCamisa").value("G"))
                // o consentimento já dado não se perde quando o cliente não o reenvia como true
                .andExpect(jsonPath("$.pessoa.consentimentoLgpdEm").exists())
                .andExpect(jsonPath("$.listaClassificacao").value("ADOLESCENTE"));
    }

    @Test
    void atualizar_corrigirDataDeNascimentoParaAdultoReclassificaParaJovem() throws Exception {
        String sede = criarCongregacao(criarArea(7), "Sede");
        String id = cadastrar(sede, "Bia", haAnos(15, 6));

        mockMvc.perform(put("/v1/api/coristas/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(sede, "Bia", haAnos(25, 0), "", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listaClassificacao").value("JOVEM"))
                .andExpect(jsonPath("$.pessoa.faixaEtaria").value("JOVEM"));
    }

    @Test
    void atualizar_adultoQueVirariaMenorExigeResponsavel() throws Exception {
        String sede = criarCongregacao(criarArea(8), "Sede");
        String id = cadastrar(sede, "Caio", haAnos(30, 0));

        mockMvc.perform(put("/v1/api/coristas/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(sede, "Caio", haAnos(15, 0), "", "")))
                .andExpect(status().isBadRequest());

        // e nada foi gravado pela tentativa
        mockMvc.perform(get("/v1/api/coristas/" + id))
                .andExpect(jsonPath("$.pessoa.faixaEtaria").value("JOVEM"))
                .andExpect(jsonPath("$.pessoa.idade").value(30));
    }

    @Test
    void atualizar_paraCongregacaoInativaRetorna409EInexistenteRetorna404() throws Exception {
        String areaId = criarArea(9);
        String sede = criarCongregacao(areaId, "Sede");
        String fechada = criarCongregacao(areaId, "Fechada");
        String id = cadastrar(sede, "Duda", haAnos(30, 0));
        mockMvc.perform(delete("/v1/api/congregacoes/" + fechada)).andExpect(status().isNoContent());

        mockMvc.perform(put("/v1/api/coristas/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(fechada, "Duda", haAnos(30, 0), "", "")))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/v1/api/coristas/00000000-0000-0000-0000-000000000000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(sede, "Duda", haAnos(30, 0), "", "")))
                .andExpect(status().isNotFound());
    }

    @Test
    void atualizar_enderecoEnviadoAtualizaOMesmoRegistroEAusenteMantem() throws Exception {
        String sede = criarCongregacao(criarArea(10), "Sede");
        String endereco = """
                , "endereco": { "logradouro": "%s", "bairro": "Boa Vista", "cidade": "Recife", "uf": "PE" }""";
        String location = postCorista(corpo(sede, "Eva", haAnos(30, 0), endereco.formatted("Rua Velha"), ""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(sede, "Eva", haAnos(30, 0), endereco.formatted("Rua Nova"), "")))
                .andExpect(jsonPath("$.pessoa.endereco.logradouro").value("Rua Nova"));

        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(sede, "Eva", haAnos(30, 0), "", "")))
                .andExpect(jsonPath("$.pessoa.endereco.logradouro").value("Rua Nova"));
    }

    // ---------- inativação ----------

    @Test
    void inativar_fazSoftDeleteMantendoOCadastroConsultavel() throws Exception {
        String sede = criarCongregacao(criarArea(11), "Sede");
        String id = cadastrar(sede, "Fabi", haAnos(30, 0));

        mockMvc.perform(delete("/v1/api/coristas/" + id)).andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/api/coristas/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pessoa.status").value("INATIVO"));
        // idempotente
        mockMvc.perform(delete("/v1/api/coristas/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/v1/api/coristas/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    // ---------- listagem ----------

    @Test
    void listar_filtraPorAreaCongregacaoListaStatusENome() throws Exception {
        String areaA = criarArea(12);
        String areaB = criarArea(13);
        String sedeA = criarCongregacao(areaA, "Sede A");
        String filialA = criarCongregacao(areaA, "Filial A");
        String sedeB = criarCongregacao(areaB, "Sede B");
        cadastrar(sedeA, "Beatriz Souza", haAnos(30, 0));
        String adolescente = cadastrar(sedeA, "Alice Lima", haAnos(15, 6));
        cadastrar(filialA, "Carlos Souza", haAnos(25, 0));
        cadastrar(sedeB, "Daniela Rocha", haAnos(28, 0));
        mockMvc.perform(delete("/v1/api/coristas/" + adolescente)).andExpect(status().isNoContent());

        // por área: só as 3 da área A, ordenadas por nome
        mockMvc.perform(get("/v1/api/coristas").param("areaId", areaA))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].pessoa.nome").value("Alice Lima"))
                .andExpect(jsonPath("$.content[1].pessoa.nome").value("Beatriz Souza"))
                .andExpect(jsonPath("$.content[2].pessoa.nome").value("Carlos Souza"));

        // por congregação
        mockMvc.perform(get("/v1/api/coristas").param("congregacaoId", filialA))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].pessoa.nome").value("Carlos Souza"));

        // por lista de classificação
        mockMvc.perform(get("/v1/api/coristas").param("areaId", areaA).param("listaClassificacao", "ADOLESCENTE"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].pessoa.nome").value("Alice Lima"));
        mockMvc.perform(get("/v1/api/coristas").param("areaId", areaA).param("listaClassificacao", "JOVEM"))
                .andExpect(jsonPath("$.page.totalElements").value(2));

        // por status (a adolescente foi inativada; as demais seguem pendentes)
        mockMvc.perform(get("/v1/api/coristas").param("areaId", areaA).param("status", "INATIVO"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].pessoa.nome").value("Alice Lima"));
        mockMvc.perform(get("/v1/api/coristas").param("areaId", areaA).param("status", "PENDENTE"))
                .andExpect(jsonPath("$.page.totalElements").value(2));

        // por trecho do nome, sem diferenciar maiúsculas
        mockMvc.perform(get("/v1/api/coristas").param("areaId", areaA).param("nome", "SOUZA"))
                .andExpect(jsonPath("$.page.totalElements").value(2));

        // % e _ digitados não viram curinga
        mockMvc.perform(get("/v1/api/coristas").param("areaId", areaA).param("nome", "%"))
                .andExpect(jsonPath("$.page.totalElements").value(0));
        mockMvc.perform(get("/v1/api/coristas").param("areaId", areaA).param("nome", "_"))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void listar_paginaOrdenandoPorNome() throws Exception {
        String areaId = criarArea(14);
        String sede = criarCongregacao(areaId, "Sede");
        for (String nome : new String[]{"Elisa", "Bruna", "Davi", "Ana", "Caio"}) {
            cadastrar(sede, nome, haAnos(30, 0));
        }

        mockMvc.perform(get("/v1/api/coristas").param("areaId", areaId).param("size", "2").param("page", "0"))
                .andExpect(jsonPath("$.page.totalElements").value(5))
                .andExpect(jsonPath("$.page.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].pessoa.nome").value("Ana"))
                .andExpect(jsonPath("$.content[1].pessoa.nome").value("Bruna"));
        mockMvc.perform(get("/v1/api/coristas").param("areaId", areaId).param("size", "2").param("page", "2"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].pessoa.nome").value("Elisa"));
    }

    @Test
    void buscarInexistente_retorna404() throws Exception {
        mockMvc.perform(get("/v1/api/coristas/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
