package com.registraai.registro_coristas_api.autenticacao;

import com.registraai.registro_coristas_api.autenticacao.email.EnviadorDeEmail;
import com.registraai.registro_coristas_api.suporte.UsuarioTesteFactory;
import com.registraai.registro_coristas_api.suporte.HierarquiaTeste;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bloqueio por tentativas, redefinição de senha por token e troca de senha, de ponta a ponta com o Postgres real. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SenhaEBloqueioIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    private static final String SENHA = "SenhaForte@123";
    private static final String NOVA_SENHA = "NovaSenha@456";
    private static final LocalDate NASCIMENTO = LocalDate.now(ZoneId.of("America/Recife")).minusYears(30).minusMonths(3);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EnviadorDeEmail enviadorDeEmail;

    private final JsonMapper json = JsonMapper.builder().build();

    private static RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.securityContext(
                UsuarioTesteFactory.contexto(new String[]{"ADMIN"}, true, new String[0]));
    }

    private String idDoLocation(String location) {
        return location.substring(location.lastIndexOf('/') + 1);
    }

    /** Cria área e congregação e devolve o id da congregação. */
    private String criarCongregacao(int numeroDaArea) throws Exception {
        String subdivisaoId = HierarquiaTeste.criarSubdivisao(mockMvc, admin());
        String congregacaoLocation = mockMvc.perform(post("/v1/api/congregacoes").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"subdivisaoId\": \"%s\", \"nome\": \"Sede %d\" }"
                                .formatted(subdivisaoId, numeroDaArea)))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        return idDoLocation(congregacaoLocation);
    }

    /** Auto-cadastra e aprova um corista; devolve o e-mail. */
    private String coristaAprovado(int numeroDaArea, String nome) throws Exception {
        String congregacaoId = criarCongregacao(numeroDaArea);
        String email = nome.toLowerCase() + "@exemplo.com";
        String corpo = """
                { "pessoa": { "nome": "%s", "dataNascimento": "%s", "congregacaoId": "%s" },
                  "tipoVoz": "SOPRANO", "tamanhoCamisa": "M",
                  "usuario": { "email": "%s", "senha": "%s" } }""".formatted(nome, NASCIMENTO, congregacaoId, email, SENHA);
        String location = mockMvc.perform(post("/v1/api/coristas").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        mockMvc.perform(patch("/v1/api/coristas/" + idDoLocation(location) + "/aprovar").with(admin()))
                .andExpect(status().isOk());
        return email;
    }

    private ResultActions login(String email, String senha) throws Exception {
        return mockMvc.perform(post("/v1/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{ \"email\": \"%s\", \"senha\": \"%s\" }".formatted(email, senha)));
    }

    private String tokenDe(String email, String senha) throws Exception {
        String corpo = login(email, senha).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("token").asString();
    }

    private ResultActions solicitarRedefinicao(String email) throws Exception {
        return mockMvc.perform(post("/v1/api/auth/redefinicao-senha/solicitar").contentType(MediaType.APPLICATION_JSON)
                .content("{ \"email\": \"%s\" }".formatted(email)));
    }

    private ResultActions confirmarRedefinicao(String token, String novaSenha) throws Exception {
        return mockMvc.perform(post("/v1/api/auth/redefinicao-senha/confirmar").contentType(MediaType.APPLICATION_JSON)
                .content("{ \"token\": \"%s\", \"novaSenha\": \"%s\" }".formatted(token, novaSenha)));
    }

    /** O token que o sistema "enviou" por e-mail (o enviador está mockado neste teste). */
    private String tokenEnviadoPara(String email) {
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(enviadorDeEmail).enviarRedefinicaoDeSenha(eq(email), token.capture());
        return token.getValue();
    }

    private ResultActions trocarSenha(String bearer, String atual, String nova) throws Exception {
        return mockMvc.perform(put("/v1/api/auth/senha").header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"senhaAtual\": \"%s\", \"novaSenha\": \"%s\" }".formatted(atual, nova)));
    }

    // ---------- bloqueio por tentativas ----------

    @Test
    void tresSenhasErradasBloqueiam_eSoARedefinicaoLibera() throws Exception {
        String email = coristaAprovado(1, "Ana");

        login(email, "Errada@111").andExpect(status().isUnauthorized());
        login(email, "Errada@222").andExpect(status().isUnauthorized());
        login(email, "Errada@333")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.status").value(423))
                .andExpect(jsonPath("$.detail").value(containsString("Redefina sua senha")));

        // bloqueada: nem a senha certa entra
        login(email, SENHA).andExpect(status().isLocked());

        solicitarRedefinicao(email).andExpect(status().isAccepted());
        String token = tokenEnviadoPara(email);

        confirmarRedefinicao(token, "fraca").andExpect(status().isBadRequest());
        confirmarRedefinicao(token, NOVA_SENHA).andExpect(status().isNoContent());

        login(email, NOVA_SENHA).andExpect(status().isOk());
        login(email, SENHA).andExpect(status().isUnauthorized());

        // o link só vale uma vez
        confirmarRedefinicao(token, "OutraSenha@789").andExpect(status().isBadRequest());
    }

    @Test
    void umAcertoZeraAContagemDeFalhas() throws Exception {
        String email = coristaAprovado(2, "Bia");

        login(email, "Errada@111").andExpect(status().isUnauthorized());
        login(email, "Errada@222").andExpect(status().isUnauthorized());
        login(email, SENHA).andExpect(status().isOk());
        login(email, "Errada@333").andExpect(status().isUnauthorized());
        login(email, "Errada@444").andExpect(status().isUnauthorized());
        login(email, SENHA).andExpect(status().isOk());
    }

    // ---------- redefinição ----------

    @Test
    void solicitarRedefinicao_emailInexistente_retorna202SemEnviarNada() throws Exception {
        solicitarRedefinicao("ninguem@exemplo.com").andExpect(status().isAccepted());

        verify(enviadorDeEmail, never()).enviarRedefinicaoDeSenha(anyString(), anyString());
    }

    @Test
    void solicitarRedefinicao_dePoucoEmPouco_naoEnviaDeNovo() throws Exception {
        String email = coristaAprovado(3, "Caio");

        solicitarRedefinicao(email).andExpect(status().isAccepted());
        solicitarRedefinicao(email).andExpect(status().isAccepted());

        verify(enviadorDeEmail, times(1)).enviarRedefinicaoDeSenha(eq(email), anyString());
    }

    @Test
    void confirmarRedefinicao_tokenQueNaoExiste_retorna400() throws Exception {
        confirmarRedefinicao("token-que-nunca-existiu", NOVA_SENHA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("inválido ou expirado")));
    }

    @Test
    void redefinirSenha_derrubaOTokenJaEmitido() throws Exception {
        String email = coristaAprovado(4, "Duda");
        String tokenAntigo = tokenDe(email, SENHA);
        mockMvc.perform(get("/v1/api/coristas/meu").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAntigo))
                .andExpect(status().isOk());

        Thread.sleep(1100); // o "iat" do JWT tem precisão de segundos
        solicitarRedefinicao(email).andExpect(status().isAccepted());
        confirmarRedefinicao(tokenEnviadoPara(email), NOVA_SENHA).andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/api/coristas/meu").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAntigo))
                .andExpect(status().isUnauthorized());
    }

    // ---------- troca de senha ----------

    @Test
    void trocarSenha_validaAtualNovaESemelhanca() throws Exception {
        String email = coristaAprovado(5, "Elis");
        String token = tokenDe(email, SENHA);

        trocarSenha(token, "Errada@111", NOVA_SENHA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A senha atual está incorreta."));
        trocarSenha(token, SENHA, "fraca").andExpect(status().isBadRequest());
        trocarSenha(token, SENHA, SENHA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A nova senha deve ser diferente da atual."));
    }

    @Test
    void trocarSenha_sucesso_novaSenhaEntra_antigaNaoEOTokenAntigoMorre() throws Exception {
        String email = coristaAprovado(6, "Fabi");
        String tokenAntigo = tokenDe(email, SENHA);

        Thread.sleep(1100); // o "iat" do JWT tem precisão de segundos
        trocarSenha(tokenAntigo, SENHA, NOVA_SENHA).andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/api/coristas/meu").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAntigo))
                .andExpect(status().isUnauthorized());
        login(email, SENHA).andExpect(status().isUnauthorized());
        String tokenNovo = tokenDe(email, NOVA_SENHA);
        assertThat(tokenNovo).isNotBlank();
        mockMvc.perform(get("/v1/api/coristas/meu").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenNovo))
                .andExpect(status().isOk());
    }

    @Test
    void trocarSenha_semLogin_retorna401() throws Exception {
        mockMvc.perform(put("/v1/api/auth/senha").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"senhaAtual\": \"%s\", \"novaSenha\": \"%s\" }".formatted(SENHA, NOVA_SENHA)))
                .andExpect(status().isUnauthorized());
    }
}
