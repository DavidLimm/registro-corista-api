package com.registraai.registro_coristas_api.usuario;

import com.jayway.jsonpath.JsonPath;
import com.registraai.registro_coristas_api.role.repository.RoleRepository;
import com.registraai.registro_coristas_api.suporte.HierarquiaTeste;
import com.registraai.registro_coristas_api.suporte.WithUsuarioTeste;
import com.registraai.registro_coristas_api.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O isolamento entre agrupamentos, com usuários reais (criados pela API, com o escopo vindo do banco): quem é da
 * Filial A não enxerga nem altera nada da Filial B. O usuário padrão da classe é um ADMIN, que enxerga tudo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@WithUsuarioTeste
class IsolamentoPorAgrupamentoIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    private static final ZoneId RECIFE = ZoneId.of("America/Recife");
    private static final String SENHA = "SenhaForte@123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    // cada teste monta o próprio par de filiais (A e B), cada uma com uma congregação e um corista
    private String filialA;
    private String filialB;
    private String congregacaoA;
    private String congregacaoB;
    private String coristaA;
    private String coristaB;
    private String pessoaA;
    private String pessoaB;
    private String sufixo;

    @BeforeEach
    void montarAsDuasFiliais() throws Exception {
        sufixo = UUID.randomUUID().toString().substring(0, 8);
        String[] a = filialComCongregacao();
        String[] b = filialComCongregacao();
        filialA = a[0];
        congregacaoA = a[1];
        filialB = b[0];
        congregacaoB = b[1];
        coristaA = cadastrarCorista(congregacaoA, "Ana " + sufixo, "ana" + sufixo + "@exemplo.com");
        coristaB = cadastrarCorista(congregacaoB, "Beto " + sufixo, "beto" + sufixo + "@exemplo.com");
        pessoaA = pessoaDoCorista(coristaA);
        pessoaB = pessoaDoCorista(coristaB);
    }

    // ---------- montagem ----------

    private String idDoLocation(String location) {
        return HierarquiaTeste.idDoLocation(location);
    }

    /** Cria uma filial com uma subdivisão e uma congregação; devolve {filialId, congregacaoId}. */
    private String[] filialComCongregacao() throws Exception {
        String filial = HierarquiaTeste.criarFilial(mockMvc);
        String subdivisaoId = HierarquiaTeste.criarSubdivisaoEm(mockMvc, filial, 1);
        String congregacao = idDoLocation(mockMvc.perform(post("/v1/api/congregacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"subdivisaoId\": \"%s\", \"nome\": \"Sede\" }".formatted(subdivisaoId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location"));
        return new String[]{idDoLocation(filial), congregacao};
    }

    /** Auto-cadastro (já com login) e aprovação pelo admin; devolve o id do corista. */
    private String cadastrarCorista(String congregacaoId, String nome, String email) throws Exception {
        String location = mockMvc.perform(post("/v1/api/coristas").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "pessoa": { "nome": "%s", "dataNascimento": "%s", "congregacaoId": "%s" },
                                  "tipoVoz": "SOPRANO", "tamanhoCamisa": "M",
                                  "usuario": { "email": "%s", "senha": "%s" } }"""
                                .formatted(nome, LocalDate.now(RECIFE).minusYears(25), congregacaoId, email, SENHA)))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        String id = idDoLocation(location);
        mockMvc.perform(patch("/v1/api/coristas/" + id + "/aprovar")).andExpect(status().isOk());
        return id;
    }

    private String pessoaDoCorista(String coristaId) throws Exception {
        return JsonPath.read(mockMvc.perform(get("/v1/api/coristas/" + coristaId))
                .andReturn().getResponse().getContentAsString(), "$.pessoa.id");
    }

    private UUID idDaRole(String nome) {
        return roleRepository.findAll().stream().filter(r -> r.getNome().equals(nome)).findFirst().orElseThrow().getId();
    }

    /** Cria o login (pelo admin) e devolve quem faz as chamadas como esse usuário real. */
    private RequestPostProcessor usuarioReal(String congregacaoId, String nome, String papel) throws Exception {
        String email = nome.toLowerCase().replace(" ", ".") + "." + sufixo + "@exemplo.com";
        String location = mockMvc.perform(post("/v1/api/pessoas").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nome\": \"%s\", \"dataNascimento\": \"%s\", \"congregacaoId\": \"%s\" }"
                                .formatted(nome, LocalDate.now(RECIFE).minusYears(30), congregacaoId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        String pessoaId = idDoLocation(location);
        mockMvc.perform(patch("/v1/api/pessoas/" + pessoaId + "/aprovar")).andExpect(status().isOk());
        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "pessoaId": "%s", "email": "%s", "senha": "%s", "roleIds": ["%s"] }"""
                                .formatted(pessoaId, email, SENHA, idDaRole(papel))))
                .andExpect(status().isCreated());
        return SecurityMockMvcRequestPostProcessors.user(usuarioRepository.findByEmail(email).orElseThrow());
    }

    private String idDoUsuario(String email) {
        return usuarioRepository.findByEmail(email).orElseThrow().getId().toString();
    }

    // ---------- líder da Filial A ----------

    @Test
    void lider_listaSoOsCoristasDoProprioAgrupamento() throws Exception {
        RequestPostProcessor liderA = usuarioReal(congregacaoA, "Lider A", "LIDER_MOCIDADE");

        mockMvc.perform(get("/v1/api/coristas").param("size", "100").with(liderA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].pessoa.nome", hasItem("Ana " + sufixo)))
                .andExpect(jsonPath("$.content[*].pessoa.nome", not(hasItem("Beto " + sufixo))));

        // o filtro do cliente não amplia o escopo: pedir a Filial B devolve vazio
        mockMvc.perform(get("/v1/api/coristas").param("agrupamentoId", filialB).with(liderA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        // o admin enxerga os dois
        mockMvc.perform(get("/v1/api/coristas").param("agrupamentoId", filialB))
                .andExpect(jsonPath("$.content[0].pessoa.nome").value("Beto " + sufixo));
    }

    @Test
    void lider_naoVeNemAlteraCoristaDeOutroAgrupamento_e404() throws Exception {
        RequestPostProcessor liderA = usuarioReal(congregacaoA, "Lider A", "LIDER_MOCIDADE");

        mockMvc.perform(get("/v1/api/coristas/" + coristaB).with(liderA)).andExpect(status().isNotFound());
        mockMvc.perform(patch("/v1/api/coristas/" + coristaB + "/reprovar").with(liderA)).andExpect(status().isNotFound());
        mockMvc.perform(patch("/v1/api/coristas/" + coristaB + "/promover").with(liderA)).andExpect(status().isNotFound());
        mockMvc.perform(patch("/v1/api/coristas/" + coristaB + "/afastar").with(liderA)).andExpect(status().isNotFound());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/v1/api/coristas/" + coristaB)
                .with(liderA)).andExpect(status().isNotFound());

        // o do próprio agrupamento segue acessível
        mockMvc.perform(get("/v1/api/coristas/" + coristaA).with(liderA)).andExpect(status().isOk());
        // e nada foi alterado no de fora
        mockMvc.perform(get("/v1/api/coristas/" + coristaB)).andExpect(jsonPath("$.pessoa.status").value("APROVADO"));
    }

    @Test
    void lider_pessoasTelefonesEEnderecosDeOutroAgrupamento_ficamInvisiveis() throws Exception {
        RequestPostProcessor liderA = usuarioReal(congregacaoA, "Lider A", "LIDER_MOCIDADE");

        mockMvc.perform(get("/v1/api/pessoas").param("size", "100").with(liderA))
                .andExpect(jsonPath("$.content[*].nome", hasItem("Ana " + sufixo)))
                .andExpect(jsonPath("$.content[*].nome", not(hasItem("Beto " + sufixo))));
        mockMvc.perform(get("/v1/api/pessoas/" + pessoaB).with(liderA)).andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/api/pessoas/" + pessoaB + "/telefones").with(liderA)).andExpect(status().isNotFound());
        mockMvc.perform(post("/v1/api/pessoas/" + pessoaB + "/telefones").with(liderA)
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"numero\": \"81999990000\" }"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/api/pessoas/" + pessoaA + "/telefones").with(liderA)).andExpect(status().isOk());
    }

    @Test
    void enderecoDePessoaDeOutroAgrupamento_ficaInvisivel() throws Exception {
        RequestPostProcessor liderA = usuarioReal(congregacaoA, "Lider A", "LIDER_MOCIDADE");
        String enderecoJson = "{ \"logradouro\": \"Rua da Aurora\", \"numero\": \"1\", \"bairro\": \"Boa Vista\","
                + " \"cidade\": \"Recife\", \"uf\": \"PE\", \"cep\": \"50050-000\" }";
        // o endereço da pessoa B é gravado pelo admin, via atualização da pessoa
        String corpoPessoaB = JsonPath.read(mockMvc.perform(get("/v1/api/pessoas/" + pessoaB)).andReturn()
                .getResponse().getContentAsString(), "$.nome");
        mockMvc.perform(put("/v1/api/pessoas/" + pessoaB).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nome\": \"%s\", \"dataNascimento\": \"%s\", \"congregacaoId\": \"%s\", \"endereco\": %s }"
                                .formatted(corpoPessoaB, LocalDate.now(RECIFE).minusYears(25), congregacaoB, enderecoJson)))
                .andExpect(status().isOk());
        String enderecoId = JsonPath.read(mockMvc.perform(get("/v1/api/pessoas/" + pessoaB)).andReturn()
                .getResponse().getContentAsString(), "$.endereco.id");

        mockMvc.perform(get("/v1/api/enderecos/" + enderecoId)).andExpect(status().isOk());
        mockMvc.perform(get("/v1/api/enderecos/" + enderecoId).with(liderA)).andExpect(status().isNotFound());
        mockMvc.perform(put("/v1/api/enderecos/" + enderecoId).with(liderA)
                        .contentType(MediaType.APPLICATION_JSON).content(enderecoJson))
                .andExpect(status().isNotFound());
    }

    @Test
    void lider_naoMudaUmaPessoaParaUmaCongregacaoDeForaDoEscopo() throws Exception {
        RequestPostProcessor liderA = usuarioReal(congregacaoA, "Lider A", "LIDER_MOCIDADE");

        mockMvc.perform(put("/v1/api/pessoas/" + pessoaA).with(liderA).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nome\": \"Ana %s\", \"dataNascimento\": \"%s\", \"congregacaoId\": \"%s\" }"
                                .formatted(sufixo, LocalDate.now(RECIFE).minusYears(25), congregacaoB)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/api/pessoas/" + pessoaA)).andExpect(jsonPath("$.congregacao.id").value(congregacaoA));
    }

    @Test
    void filaDeAfastamento_soMostraPedidosDoProprioAgrupamento() throws Exception {
        RequestPostProcessor liderA = usuarioReal(congregacaoA, "Lider A", "LIDER_MOCIDADE");
        // o admin registra um pedido em cada filial
        for (String corista : List.of(coristaA, coristaB)) {
            mockMvc.perform(post("/v1/api/coristas/" + corista + "/solicitar-afastamento")
                            .contentType(MediaType.APPLICATION_JSON).content("{ \"motivo\": \"Viagem\" }"))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/v1/api/solicitacoes-afastamento").param("status", "PENDENTE").with(liderA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].pessoaNome", hasItem("Ana " + sufixo)))
                .andExpect(jsonPath("$.content[*].pessoaNome", not(hasItem("Beto " + sufixo))));
        mockMvc.perform(post("/v1/api/coristas/" + coristaB + "/solicitar-afastamento").with(liderA)
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"motivo\": \"x\" }"))
                .andExpect(status().isNotFound());
    }

    // ---------- pastor da Filial A ----------

    @Test
    void pastor_soGerenciaCongregacoesEUsuariosDoProprioAgrupamento() throws Exception {
        RequestPostProcessor pastorA = usuarioReal(congregacaoA, "Pastor A", "PASTOR");
        // o pastor não ganha vínculo automático: o admin o designa para a Filial A
        String pastorId = idDoUsuario("pastor.a." + sufixo + "@exemplo.com");
        mockMvc.perform(put("/v1/api/usuarios/" + pastorId + "/agrupamentos").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"agrupamentoIds\": [\"%s\"] }".formatted(filialA)))
                .andExpect(status().isOk());
        String subdivisaoA = JsonPath.<List<String>>read(mockMvc.perform(get("/v1/api/agrupamentos/" + filialA + "/subdivisoes"))
                .andReturn().getResponse().getContentAsString(), "$[*].id").get(0);
        String subdivisaoB = JsonPath.<List<String>>read(mockMvc.perform(get("/v1/api/agrupamentos/" + filialB + "/subdivisoes"))
                .andReturn().getResponse().getContentAsString(), "$[*].id").get(0);

        // congregações: cria na própria filial, não na outra (403)
        mockMvc.perform(post("/v1/api/congregacoes").with(pastorA).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"subdivisaoId\": \"%s\", \"nome\": \"Nova A\" }".formatted(subdivisaoA)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/v1/api/congregacoes").with(pastorA).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"subdivisaoId\": \"%s\", \"nome\": \"Invasora\" }".formatted(subdivisaoB)))
                .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/v1/api/congregacoes/" + congregacaoB)
                .with(pastorA)).andExpect(status().isForbidden());

        // usuários: lista só os do próprio agrupamento; o de fora é 404
        mockMvc.perform(get("/v1/api/usuarios").param("size", "100").with(pastorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].email", hasItem("ana" + sufixo + "@exemplo.com")))
                .andExpect(jsonPath("$.content[*].email", not(hasItem("beto" + sufixo + "@exemplo.com"))));
        mockMvc.perform(get("/v1/api/usuarios/" + idDoUsuario("beto" + sufixo + "@exemplo.com")).with(pastorA))
                .andExpect(status().isNotFound());
    }

    @Test
    void pastorSemVinculos_naoEnxergaNada() throws Exception {
        RequestPostProcessor pastorSemEscopo = usuarioReal(congregacaoA, "Pastor Sem", "PASTOR");

        mockMvc.perform(get("/v1/api/coristas").with(pastorSemEscopo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(get("/v1/api/pessoas").with(pastorSemEscopo))
                .andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(get("/v1/api/coristas/" + coristaA).with(pastorSemEscopo)).andExpect(status().isNotFound());
    }

    // ---------- PASTOR_ADMIN ----------

    @Test
    void pastorAdmin_enxergaTodosOsAgrupamentos_masNaoAlteraDados() throws Exception {
        RequestPostProcessor pastorAdmin = usuarioReal(congregacaoA, "Pastor Geral", "PASTOR_ADMIN");

        mockMvc.perform(get("/v1/api/coristas").param("size", "100").with(pastorAdmin))
                .andExpect(jsonPath("$.content[*].pessoa.nome", hasItem("Ana " + sufixo)))
                .andExpect(jsonPath("$.content[*].pessoa.nome", hasItem("Beto " + sufixo)));
        mockMvc.perform(get("/v1/api/coristas/" + coristaB).with(pastorAdmin)).andExpect(status().isOk());

        // só leitura: aprovar, reprovar, afastar, promover e editar são negados por permissão
        mockMvc.perform(patch("/v1/api/coristas/" + coristaB + "/reprovar").with(pastorAdmin)).andExpect(status().isForbidden());
        mockMvc.perform(patch("/v1/api/coristas/" + coristaB + "/afastar").with(pastorAdmin)).andExpect(status().isForbidden());
        mockMvc.perform(patch("/v1/api/coristas/" + coristaB + "/promover").with(pastorAdmin)).andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/v1/api/coristas/" + coristaB)
                .with(pastorAdmin)).andExpect(status().isForbidden());
        mockMvc.perform(patch("/v1/api/pessoas/" + pessoaB + "/reprovar").with(pastorAdmin)).andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/api/coristas/" + coristaB)).andExpect(jsonPath("$.pessoa.status").value("APROVADO"));
    }

    // ---------- público ----------

    @Test
    void listaDeCongregacoes_continuaPublicaComTodasAsFiliais() throws Exception {
        mockMvc.perform(get("/v1/api/congregacoes").param("agrupamentoId", filialA).with(SecurityMockMvcRequestPostProcessors.anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(congregacaoA)));
        mockMvc.perform(get("/v1/api/congregacoes").param("agrupamentoId", filialB).with(SecurityMockMvcRequestPostProcessors.anonymous()))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(congregacaoB)));
    }
}
