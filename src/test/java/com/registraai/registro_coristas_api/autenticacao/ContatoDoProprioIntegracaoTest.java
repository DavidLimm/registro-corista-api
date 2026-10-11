package com.registraai.registro_coristas_api.autenticacao;

import com.registraai.registro_coristas_api.suporte.UsuarioTesteFactory;
import com.registraai.registro_coristas_api.suporte.HierarquiaTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O corista edita o próprio telefone e endereço (no máximo uma vez a cada 10 dias), e nunca os de outra pessoa. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ContatoDoProprioIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    private static final String SENHA = "SenhaForte@123";
    private static final LocalDate NASCIMENTO = LocalDate.now(ZoneId.of("America/Recife")).minusYears(30).minusMonths(3);
    private static final String ENDERECO = """
            { "logradouro": "Rua da Aurora", "bairro": "Boa Vista", "cidade": "Recife", "uf": "PE", "cep": "50050-000" }""";

    @Autowired
    private MockMvc mockMvc;

    private final JsonMapper json = JsonMapper.builder().build();

    private static RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.securityContext(
                UsuarioTesteFactory.contexto(new String[]{"ADMIN"}, true, new String[0]));
    }

    private String idDoLocation(String location) {
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private String criarCongregacao(int numeroDaArea) throws Exception {
        String subdivisaoId = HierarquiaTeste.criarSubdivisao(mockMvc, admin());
        String congregacaoLocation = mockMvc.perform(post("/v1/api/congregacoes").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"subdivisaoId\": \"%s\", \"nome\": \"Sede %d\" }"
                                .formatted(subdivisaoId, numeroDaArea)))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        return idDoLocation(congregacaoLocation);
    }

    /** Um corista aprovado, já com endereço. Devolve {token, pessoaId, enderecoId}. */
    private String[] coristaAprovado(String congregacaoId, String nome) throws Exception {
        String email = nome.toLowerCase() + "@exemplo.com";
        String corpo = """
                { "pessoa": { "nome": "%s", "dataNascimento": "%s", "congregacaoId": "%s", "endereco": %s },
                  "tipoVoz": "SOPRANO", "tamanhoCamisa": "M",
                  "usuario": { "email": "%s", "senha": "%s" } }""".formatted(nome, NASCIMENTO, congregacaoId, ENDERECO, email, SENHA);
        String resposta = mockMvc.perform(post("/v1/api/coristas").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String coristaId = json.readTree(resposta).get("id").asString();
        String pessoaId = json.readTree(resposta).get("pessoa").get("id").asString();
        String enderecoId = json.readTree(resposta).get("pessoa").get("endereco").get("id").asString();
        mockMvc.perform(patch("/v1/api/coristas/" + coristaId + "/aprovar").with(admin())).andExpect(status().isOk());

        String login = mockMvc.perform(post("/v1/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"%s\", \"senha\": \"%s\" }".formatted(email, SENHA)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new String[]{json.readTree(login).get("token").asString(), pessoaId, enderecoId};
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private ResultActions criarTelefone(String token, String pessoaId, String numero) throws Exception {
        return mockMvc.perform(post("/v1/api/pessoas/" + pessoaId + "/telefones")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"numero\": \"%s\", \"whatsapp\": true }".formatted(numero)));
    }

    private ResultActions trocarEndereco(String token, String enderecoId, String logradouro) throws Exception {
        return mockMvc.perform(put("/v1/api/enderecos/" + enderecoId).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(ENDERECO.replace("Rua da Aurora", logradouro)));
    }

    @Test
    void corista_cadastraOProprioTelefoneUmaVez_aSegundaAlteracaoEmDezDiasEBarrada() throws Exception {
        String[] ana = coristaAprovado(criarCongregacao(1), "Ana");

        criarTelefone(ana[0], ana[1], "(81) 99999-0001").andExpect(status().isCreated());

        criarTelefone(ana[0], ana[1], "(81) 99999-0002")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").value(containsString("seu telefone")));

        // listar o próprio continua livre
        mockMvc.perform(get("/v1/api/pessoas/" + ana[1] + "/telefones").header(HttpHeaders.AUTHORIZATION, bearer(ana[0])))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void corista_trocaOProprioEndereco_masSoMudancaDeVerdadeConta() throws Exception {
        String[] bia = coristaAprovado(criarCongregacao(2), "Bia");

        // reenviar o mesmo endereço não é alteração
        trocarEndereco(bia[0], bia[2], "Rua da Aurora").andExpect(status().isOk());
        trocarEndereco(bia[0], bia[2], "Rua da Aurora").andExpect(status().isOk());

        trocarEndereco(bia[0], bia[2], "Rua Nova").andExpect(status().isOk())
                .andExpect(jsonPath("$.logradouro").value("Rua Nova"));
        trocarEndereco(bia[0], bia[2], "Rua Mais Nova")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value(containsString("seu endereço")));
    }

    @Test
    void corista_naoMexeNoContatoDeOutraPessoa() throws Exception {
        String congregacaoId = criarCongregacao(3);
        String[] caio = coristaAprovado(congregacaoId, "Caio");
        String[] duda = coristaAprovado(congregacaoId, "Duda");

        criarTelefone(caio[0], duda[1], "(81) 99999-0003").andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/api/pessoas/" + duda[1] + "/telefones").header(HttpHeaders.AUTHORIZATION, bearer(caio[0])))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/v1/api/pessoas/" + duda[1] + "/telefones/" + duda[1])
                        .header(HttpHeaders.AUTHORIZATION, bearer(caio[0])))
                .andExpect(status().isForbidden());
        trocarEndereco(caio[0], duda[2], "Rua do Caio").andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/api/enderecos/" + duda[2]).header(HttpHeaders.AUTHORIZATION, bearer(caio[0])))
                .andExpect(status().isForbidden());
    }

    @Test
    void lideranca_editaOContatoDeOutroSemLimite() throws Exception {
        String[] elis = coristaAprovado(criarCongregacao(4), "Elis");

        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/v1/api/pessoas/" + elis[1] + "/telefones").with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"numero\": \"(81) 99999-100%d\" }".formatted(i)))
                    .andExpect(status().isCreated());
        }
    }
}
