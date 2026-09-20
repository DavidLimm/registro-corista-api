package com.registraai.registro_coristas_api.telefone;

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

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo completo Controller → Service → Repository → Postgres real (Flyway aplica o schema). Ainda não há endpoint
 * de pessoa, então cada teste obtém uma pessoa cadastrando um corista adulto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TelefoneIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    // o banco é compartilhado pelos testes da classe; cada pessoa nasce numa área nova para não haver conflito
    private static final AtomicInteger PROXIMA_AREA = new AtomicInteger(1);

    @Autowired
    private MockMvc mockMvc;

    private String idDoLocation(String location) {
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private String criarPessoa() throws Exception {
        int numeroDaArea = PROXIMA_AREA.getAndIncrement();
        String areaId = idDoLocation(mockMvc.perform(post("/v1/api/areas").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"numero\": %d, \"nome\": \"Área %d\" }".formatted(numeroDaArea, numeroDaArea)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location"));
        String congregacaoId = idDoLocation(mockMvc.perform(post("/v1/api/congregacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"areaId\": \"%s\", \"nome\": \"Sede\" }".formatted(areaId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location"));
        String corpo = mockMvc.perform(post("/v1/api/coristas").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "pessoa": { "nome": "Maria da Silva", "dataNascimento": "1995-05-17",
                                              "congregacaoId": "%s" },
                                  "tipoVoz": "SOPRANO", "tamanhoCamisa": "M" }""".formatted(congregacaoId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.pessoa.id");
    }

    private String base(String pessoaId) {
        return "/v1/api/pessoas/" + pessoaId + "/telefones";
    }

    private ResultActions postTelefone(String pessoaId, String numero, boolean whatsapp, boolean principal)
            throws Exception {
        return mockMvc.perform(post(base(pessoaId)).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"numero\": \"%s\", \"whatsapp\": %s, \"principal\": %s }"
                        .formatted(numero, whatsapp, principal)));
    }

    /** Cadastra e devolve o id do telefone. */
    private String cadastrar(String pessoaId, String numero, boolean whatsapp, boolean principal) throws Exception {
        return idDoLocation(postTelefone(pessoaId, numero, whatsapp, principal)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location"));
    }

    @Test
    void criarBuscarEListar_gravandoSoOsDigitosEFazendoOPrimeiroSerPrincipal() throws Exception {
        String pessoaId = criarPessoa();

        String location = postTelefone(pessoaId, "(81) 99999-0000", true, false)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.pessoaId").value(pessoaId))
                .andExpect(jsonPath("$.numero").value("81999990000"))
                .andExpect(jsonPath("$.whatsapp").value(true))
                .andExpect(jsonPath("$.principal").value(true))
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("81999990000"))
                .andExpect(jsonPath("$.principal").value(true));

        mockMvc.perform(get(base(pessoaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void segundoTelefone_naoEPrincipal_eListaColocaOPrincipalPrimeiro() throws Exception {
        String pessoaId = criarPessoa();
        cadastrar(pessoaId, "81999990000", true, false);

        postTelefone(pessoaId, "8133334444", false, false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.principal").value(false));

        mockMvc.perform(get(base(pessoaId)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].numero").value("81999990000"))
                .andExpect(jsonPath("$[0].principal").value(true))
                .andExpect(jsonPath("$[1].numero").value("8133334444"))
                .andExpect(jsonPath("$[1].principal").value(false));
    }

    @Test
    void criarComPrincipal_trocaOPrincipalSemViolarOIndiceUnico() throws Exception {
        String pessoaId = criarPessoa();
        cadastrar(pessoaId, "81999990000", true, false);

        postTelefone(pessoaId, "8133334444", false, true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.principal").value(true));

        mockMvc.perform(get(base(pessoaId)))
                .andExpect(jsonPath("$[0].numero").value("8133334444"))
                .andExpect(jsonPath("$[0].principal").value(true))
                .andExpect(jsonPath("$[1].numero").value("81999990000"))
                .andExpect(jsonPath("$[1].principal").value(false));
    }

    @Test
    void atualizar_alteraOsDadosEMarcarOutroComoPrincipalTrocaOPrincipal() throws Exception {
        String pessoaId = criarPessoa();
        cadastrar(pessoaId, "81999990000", false, false);
        String segundo = cadastrar(pessoaId, "8133334444", false, false);

        mockMvc.perform(put(base(pessoaId) + "/" + segundo).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"numero\": \"(81) 98888-7777\", \"whatsapp\": true, \"principal\": true }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("81988887777"))
                .andExpect(jsonPath("$.whatsapp").value(true))
                .andExpect(jsonPath("$.principal").value(true));

        mockMvc.perform(get(base(pessoaId)))
                .andExpect(jsonPath("$[0].numero").value("81988887777"))
                .andExpect(jsonPath("$[0].principal").value(true))
                .andExpect(jsonPath("$[1].numero").value("81999990000"))
                .andExpect(jsonPath("$[1].principal").value(false));
    }

    @Test
    void atualizar_principalFalseNoPrincipal_naoDeixaAPessoaSemPrincipal() throws Exception {
        String pessoaId = criarPessoa();
        String principal = cadastrar(pessoaId, "81999990000", false, false);

        mockMvc.perform(put(base(pessoaId) + "/" + principal).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"numero\": \"81999990000\", \"whatsapp\": false, \"principal\": false }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal").value(true));
    }

    @Test
    void numeroRepetidoParaAMesmaPessoa_retorna409NaCriacaoENaAtualizacao() throws Exception {
        String pessoaId = criarPessoa();
        cadastrar(pessoaId, "81999990000", false, false);
        String segundo = cadastrar(pessoaId, "8133334444", false, false);

        // mesmo número escrito de outro jeito continua sendo o mesmo número
        postTelefone(pessoaId, "(81) 99999-0000", false, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(put(base(pessoaId) + "/" + segundo).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"numero\": \"81999990000\" }"))
                .andExpect(status().isConflict());

        // reenviar o próprio número na edição não é conflito
        mockMvc.perform(put(base(pessoaId) + "/" + segundo).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"numero\": \"(81) 3333-4444\", \"whatsapp\": true }"))
                .andExpect(status().isOk());
    }

    @Test
    void mesmoNumeroEmPessoasDiferentes_eAceito() throws Exception {
        String primeira = criarPessoa();
        String segunda = criarPessoa();

        cadastrar(primeira, "81999990000", false, false);
        cadastrar(segunda, "81999990000", false, false);
    }

    @Test
    void remover_naoPrincipal_mantemOPrincipal() throws Exception {
        String pessoaId = criarPessoa();
        cadastrar(pessoaId, "81999990000", false, false);
        String secundario = cadastrar(pessoaId, "8133334444", false, false);

        mockMvc.perform(delete(base(pessoaId) + "/" + secundario)).andExpect(status().isNoContent());

        mockMvc.perform(get(base(pessoaId) + "/" + secundario)).andExpect(status().isNotFound());
        mockMvc.perform(get(base(pessoaId)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].numero").value("81999990000"))
                .andExpect(jsonPath("$[0].principal").value(true));
    }

    @Test
    void remover_principal_promoveOMaisAntigoDosRestantes() throws Exception {
        String pessoaId = criarPessoa();
        String principal = cadastrar(pessoaId, "81999990000", false, false);
        cadastrar(pessoaId, "8133334444", false, false);
        cadastrar(pessoaId, "81988887777", false, false);

        mockMvc.perform(delete(base(pessoaId) + "/" + principal)).andExpect(status().isNoContent());

        mockMvc.perform(get(base(pessoaId)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].numero").value("8133334444"))
                .andExpect(jsonPath("$[0].principal").value(true))
                .andExpect(jsonPath("$[1].numero").value("81988887777"))
                .andExpect(jsonPath("$[1].principal").value(false));
    }

    @Test
    void remover_unicoTelefone_deixaAPessoaSemTelefonesEONovoVoltaAserPrincipal() throws Exception {
        String pessoaId = criarPessoa();
        String unico = cadastrar(pessoaId, "81999990000", false, false);

        mockMvc.perform(delete(base(pessoaId) + "/" + unico)).andExpect(status().isNoContent());
        mockMvc.perform(get(base(pessoaId))).andExpect(jsonPath("$.length()").value(0));

        postTelefone(pessoaId, "8133334444", false, false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.principal").value(true));
    }

    @Test
    void telefoneDeOutraPessoa_naoEAcessivelPelaRotaDeUmaTerceira() throws Exception {
        String dona = criarPessoa();
        String outra = criarPessoa();
        String telefoneId = cadastrar(dona, "81999990000", false, false);

        mockMvc.perform(get(base(outra) + "/" + telefoneId)).andExpect(status().isNotFound());
        mockMvc.perform(put(base(outra) + "/" + telefoneId).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"numero\": \"8133334444\" }"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(base(outra) + "/" + telefoneId)).andExpect(status().isNotFound());

        // e nada mudou para a dona
        mockMvc.perform(get(base(dona) + "/" + telefoneId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("81999990000"));
    }

    @Test
    void pessoaInexistente_retorna404EmTodasAsRotasQueAExigem() throws Exception {
        String pessoaInexistente = UUID.randomUUID().toString();

        postTelefone(pessoaInexistente, "81999990000", false, false).andExpect(status().isNotFound());
        mockMvc.perform(get(base(pessoaInexistente))).andExpect(status().isNotFound());
    }

    @Test
    void numeroInvalido_retorna400() throws Exception {
        String pessoaId = criarPessoa();

        postTelefone(pessoaId, "12345", false, false).andExpect(status().isBadRequest());
    }
}
