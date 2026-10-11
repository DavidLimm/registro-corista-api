package com.registraai.registro_coristas_api.congregacao;

import com.jayway.jsonpath.JsonPath;
import com.registraai.registro_coristas_api.suporte.HierarquiaTeste;
import com.registraai.registro_coristas_api.suporte.WithUsuarioTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fluxo completo Controller → Service → Repository → Postgres real (Flyway aplica o schema). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@WithUsuarioTeste
class CongregacaoIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    private static final String ENDERECO_JSON = """
            , "endereco": { "logradouro": "%s", "numero": "123", "bairro": "Boa Vista",
                            "cidade": "Recife", "uf": "pe", "cep": "50050-000" }""";

    /** Cria uma filial (nome único) com uma subdivisão pela API e devolve o id da subdivisão. */
    private String criarSubdivisao() throws Exception {
        return HierarquiaTeste.criarSubdivisao(mockMvc);
    }

    private String corpo(String subdivisaoId, String nome, String enderecoJson) {
        return "{ \"subdivisaoId\": \"%s\", \"nome\": \"%s\"%s }".formatted(subdivisaoId, nome, enderecoJson);
    }

    /** Cria uma congregação sem endereço pela API e devolve o Location. */
    private String criarCongregacao(String subdivisaoId, String nome) throws Exception {
        return mockMvc.perform(post("/v1/api/congregacoes").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(subdivisaoId, nome, "")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
    }

    @Test
    void criarBuscarEAtualizar_gravaEnderecoAninhadoNormalizadoEMantemQuandoAusente() throws Exception {
        String subdivisaoId = criarSubdivisao();

        MvcResult criada = mockMvc.perform(post("/v1/api/congregacoes").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(subdivisaoId, "  Sede  ", ENDERECO_JSON.formatted("Rua da Aurora"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Sede"))
                .andExpect(jsonPath("$.ativa").value(true))
                .andExpect(jsonPath("$.subdivisao.id").value(subdivisaoId))
                .andExpect(jsonPath("$.subdivisao.numero").value(1))
                .andExpect(jsonPath("$.agrupamento.tipo").value("FILIAL"))
                .andExpect(jsonPath("$.endereco.uf").value("PE"))
                .andExpect(jsonPath("$.endereco.cep").value("50050000"))
                .andReturn();
        String location = criada.getResponse().getHeader("Location");
        String enderecoId = JsonPath.read(criada.getResponse().getContentAsString(), "$.endereco.id");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endereco.logradouro").value("Rua da Aurora"));

        // editar com novo endereço atualiza o mesmo registro de endereço
        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(subdivisaoId, "Sede Renomeada", ENDERECO_JSON.formatted("Rua Nova"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Sede Renomeada"))
                .andExpect(jsonPath("$.endereco.id").value(enderecoId))
                .andExpect(jsonPath("$.endereco.logradouro").value("Rua Nova"));

        // editar sem endereço no corpo mantém o atual
        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(subdivisaoId, "Sede Renomeada 2", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Sede Renomeada 2"))
                .andExpect(jsonPath("$.endereco.id").value(enderecoId))
                .andExpect(jsonPath("$.endereco.logradouro").value("Rua Nova"));
    }

    @Test
    void congregacaoNaAreaSemeada_usaASubdivisaoImplicita() throws Exception {
        // o formulário: tipo AREA → escolhe a Área 40 entre as 74 → (subdivisão implícita) → nome da congregação
        String agrupamentos = mockMvc.perform(get("/v1/api/agrupamentos").param("tipo", "AREA"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String area40Id = JsonPath.<java.util.List<String>>read(agrupamentos, "$[?(@.numero==40)].id").get(0);
        String subdivisoes = mockMvc.perform(get("/v1/api/agrupamentos/" + area40Id + "/subdivisoes"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String subdivisaoId = JsonPath.read(subdivisoes, "$[0].id");

        mockMvc.perform(post("/v1/api/congregacoes").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(subdivisaoId, "Acampamento 2 (teste)", "")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.agrupamento.tipo").value("AREA"))
                .andExpect(jsonPath("$.agrupamento.numero").value(40))
                .andExpect(jsonPath("$.agrupamento.id").value(area40Id));
    }

    @Test
    void editarCongregacaoSemEnderecoComEnderecoNoCorpo_criaEVincula() throws Exception {
        String subdivisaoId = criarSubdivisao();
        String location = criarCongregacao(subdivisaoId, "Sem endereço");

        mockMvc.perform(get(location)).andExpect(jsonPath("$.endereco").doesNotExist());

        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(subdivisaoId, "Sem endereço", ENDERECO_JSON.formatted("Rua Criada"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endereco.logradouro").value("Rua Criada"));
    }

    @Test
    void nomeDuplicado_naMesmaSubdivisaoDa409SemDiferenciarMaiusculasEEmOutraPermite() throws Exception {
        String subdivisaoA = criarSubdivisao();
        String subdivisaoB = criarSubdivisao();
        String location = criarCongregacao(subdivisaoA, "Sede");

        mockMvc.perform(post("/v1/api/congregacoes").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(subdivisaoA, "SEDE", "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("Já existe uma congregação chamada")));

        // mesmo nome em outra subdivisão é permitido
        criarCongregacao(subdivisaoB, "Sede");

        // regravar o próprio nome não é conflito
        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(subdivisaoA, "Sede", "")))
                .andExpect(status().isOk());
    }

    @Test
    void criar_emSubdivisaoOuAgrupamentoInativo_retorna409EEmSubdivisaoInexistente_retorna404() throws Exception {
        String filial = HierarquiaTeste.criarFilial(mockMvc);
        String subdivisaoId = HierarquiaTeste.criarSubdivisaoEm(mockMvc, filial, 1);
        String outraSubdivisaoId = HierarquiaTeste.criarSubdivisaoEm(mockMvc, filial, 2);

        // subdivisão inativa
        mockMvc.perform(delete(filial + "/subdivisoes/" + subdivisaoId)).andExpect(status().isNoContent());
        mockMvc.perform(post("/v1/api/congregacoes").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(subdivisaoId, "Sede", "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("inativa")));

        // agrupamento inativo (mesmo com a subdivisão ativa)
        mockMvc.perform(delete(filial)).andExpect(status().isNoContent());
        mockMvc.perform(post("/v1/api/congregacoes").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(outraSubdivisaoId, "Sede", "")))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/v1/api/congregacoes").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("00000000-0000-0000-0000-000000000000", "Sede", "")))
                .andExpect(status().isNotFound());
    }

    @Test
    void remanejar_liberaAInativacaoDaSubdivisaoDeOrigem() throws Exception {
        String filial = HierarquiaTeste.criarFilial(mockMvc);
        String origem = HierarquiaTeste.criarSubdivisaoEm(mockMvc, filial, 1);
        String destino = HierarquiaTeste.criarSubdivisaoEm(mockMvc, filial, 2);
        String location = criarCongregacao(origem, "Sede da Área 1");

        // subdivisão e agrupamento com congregação ativa não podem ser inativados
        mockMvc.perform(delete(filial + "/subdivisoes/" + origem)).andExpect(status().isConflict());
        mockMvc.perform(delete(filial)).andExpect(status().isConflict());

        // remanejar = editar a congregação apontando para outra subdivisão ativa
        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(destino, "Sede da Área 1", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subdivisao.id").value(destino))
                .andExpect(jsonPath("$.subdivisao.numero").value(2));

        mockMvc.perform(delete(filial + "/subdivisoes/" + origem)).andExpect(status().isNoContent());
        // e a subdivisão que recebeu a congregação passa a ser a bloqueada
        mockMvc.perform(delete(filial + "/subdivisoes/" + destino)).andExpect(status().isConflict());

        // não é possível remanejar de volta para a subdivisão agora inativa
        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(origem, "Sede da Área 1", "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("inativa")));
        mockMvc.perform(get(location)).andExpect(jsonPath("$.subdivisao.id").value(destino));
    }

    @Test
    void remanejar_paraSubdivisaoOndeOMesmoNomeJaExiste_retorna409() throws Exception {
        String subdivisaoA = criarSubdivisao();
        String subdivisaoB = criarSubdivisao();
        String location = criarCongregacao(subdivisaoA, "Sede");
        criarCongregacao(subdivisaoB, "Sede");

        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(subdivisaoB, "Sede", "")))
                .andExpect(status().isConflict());
    }

    @Test
    void inativarEReativar_eReativarExigeSubdivisaoEAgrupamentoAtivos() throws Exception {
        String filial = HierarquiaTeste.criarFilial(mockMvc);
        String subdivisaoId = HierarquiaTeste.criarSubdivisaoEm(mockMvc, filial, 1);
        String location = criarCongregacao(subdivisaoId, "Sede");

        mockMvc.perform(delete(location)).andExpect(status().isNoContent());
        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativa").value(false));

        // congregação inativa não bloqueia a inativação do agrupamento
        mockMvc.perform(delete(filial)).andExpect(status().isNoContent());

        // mas não dá para reativar a congregação enquanto o agrupamento estiver inativo
        mockMvc.perform(patch(location + "/reativar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("inativa")));

        mockMvc.perform(patch(filial + "/reativar")).andExpect(status().isOk());
        mockMvc.perform(patch(location + "/reativar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativa").value(true));
    }

    @Test
    void listar_filtraPorAgrupamentoSubdivisaoESituacaoOrdenandoPorNome() throws Exception {
        String filial = HierarquiaTeste.criarFilial(mockMvc);
        String filialId = HierarquiaTeste.idDoLocation(filial);
        String subdivisaoId = HierarquiaTeste.criarSubdivisaoEm(mockMvc, filial, 1);
        String segundaSubdivisao = HierarquiaTeste.criarSubdivisaoEm(mockMvc, filial, 2);
        String outraSubdivisao = criarSubdivisao();
        criarCongregacao(subdivisaoId, "Bairro Novo");
        String fechada = criarCongregacao(subdivisaoId, "Antiga");
        criarCongregacao(subdivisaoId, "Centro");
        criarCongregacao(segundaSubdivisao, "Da segunda área");
        criarCongregacao(outraSubdivisao, "De outro agrupamento");
        mockMvc.perform(delete(fechada)).andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/api/congregacoes").param("subdivisaoId", subdivisaoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].nome").value("Antiga"))
                .andExpect(jsonPath("$[1].nome").value("Bairro Novo"))
                .andExpect(jsonPath("$[2].nome").value("Centro"));

        // pelo agrupamento vêm as congregações de todas as subdivisões dele
        mockMvc.perform(get("/v1/api/congregacoes").param("agrupamentoId", filialId))
                .andExpect(jsonPath("$.length()").value(4));

        mockMvc.perform(get("/v1/api/congregacoes").param("subdivisaoId", subdivisaoId).param("ativa", "true"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nome").value("Bairro Novo"));

        mockMvc.perform(get("/v1/api/congregacoes").param("subdivisaoId", subdivisaoId).param("ativa", "false"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Antiga"));
    }

    @Test
    void buscarInexistente_retorna404() throws Exception {
        String corpoResposta = mockMvc.perform(get("/v1/api/congregacoes/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andReturn().getResponse().getContentAsString();

        assertThat(corpoResposta).contains("Congregação não encontrada");
    }
}
