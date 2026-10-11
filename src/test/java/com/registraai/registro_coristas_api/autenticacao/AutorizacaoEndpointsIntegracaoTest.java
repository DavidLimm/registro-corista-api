package com.registraai.registro_coristas_api.autenticacao;

import com.registraai.registro_coristas_api.suporte.UsuarioTesteFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confere, endpoint por endpoint, que cada {@code @PreAuthorize} pede a permissão certa: sem login é 401, logado sem a
 * permissão é 403, e com a permissão a requisição passa pela segurança (a resposta é outra, ex.: 404 de id
 * inexistente). Só endpoints sem corpo: com corpo inválido o 400 da validação vem antes do {@code @PreAuthorize}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AutorizacaoEndpointsIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    private static final String ID = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mockMvc;

    static Stream<Arguments> endpoints() {
        return Stream.of(
                // coristas (o GET por id e o PUT têm a regra extra "ou é o próprio" e são testados à parte)
                alvo(HttpMethod.GET, "/v1/api/coristas", "CORISTA_LER"),
                alvo(HttpMethod.DELETE, "/v1/api/coristas/" + ID, "CORISTA_INATIVAR"),
                alvo(HttpMethod.PATCH, "/v1/api/coristas/" + ID + "/aprovar", "CORISTA_APROVAR"),
                alvo(HttpMethod.PATCH, "/v1/api/coristas/" + ID + "/reprovar", "CORISTA_REPROVAR"),
                alvo(HttpMethod.PATCH, "/v1/api/coristas/" + ID + "/promover", "CORISTA_PROMOVER"),
                alvo(HttpMethod.PATCH, "/v1/api/coristas/" + ID + "/afastar", "CORISTA_APROVAR_AFASTAMENTO"),
                alvo(HttpMethod.PATCH, "/v1/api/coristas/" + ID + "/retornar-do-afastamento", "CORISTA_APROVAR_AFASTAMENTO"),
                // pessoas
                alvo(HttpMethod.GET, "/v1/api/pessoas", "PESSOA_LER"),
                alvo(HttpMethod.GET, "/v1/api/pessoas/" + ID, "PESSOA_LER"),
                alvo(HttpMethod.DELETE, "/v1/api/pessoas/" + ID, "PESSOA_INATIVAR"),
                alvo(HttpMethod.PATCH, "/v1/api/pessoas/" + ID + "/aprovar", "PESSOA_APROVAR"),
                alvo(HttpMethod.PATCH, "/v1/api/pessoas/" + ID + "/reprovar", "PESSOA_REPROVAR"),
                alvo(HttpMethod.PATCH, "/v1/api/pessoas/" + ID + "/afastar", "CORISTA_APROVAR_AFASTAMENTO"),
                alvo(HttpMethod.PATCH, "/v1/api/pessoas/" + ID + "/retornar-do-afastamento", "CORISTA_APROVAR_AFASTAMENTO"),
                // telefones
                alvo(HttpMethod.GET, "/v1/api/pessoas/" + ID + "/telefones", "PESSOA_LER"),
                alvo(HttpMethod.DELETE, "/v1/api/pessoas/" + ID + "/telefones/" + ID, "PESSOA_ATUALIZAR"),
                // logins
                alvo(HttpMethod.GET, "/v1/api/usuarios", "USUARIO_GERENCIAR"),
                alvo(HttpMethod.GET, "/v1/api/usuarios/" + ID, "USUARIO_GERENCIAR"),
                alvo(HttpMethod.DELETE, "/v1/api/usuarios/" + ID, "USUARIO_GERENCIAR"),
                alvo(HttpMethod.PATCH, "/v1/api/usuarios/" + ID + "/reativar", "USUARIO_GERENCIAR"),
                // estrutura da igreja e papéis
                alvo(HttpMethod.DELETE, "/v1/api/areas/" + ID, "AREA_GERENCIAR"),
                alvo(HttpMethod.PATCH, "/v1/api/areas/" + ID + "/reativar", "AREA_GERENCIAR"),
                alvo(HttpMethod.DELETE, "/v1/api/congregacoes/" + ID, "CONGREGACAO_GERENCIAR"),
                alvo(HttpMethod.PATCH, "/v1/api/congregacoes/" + ID + "/reativar", "CONGREGACAO_GERENCIAR"),
                alvo(HttpMethod.DELETE, "/v1/api/roles/" + ID, "ROLE_GERENCIAR_PERMISSOES"),
                alvo(HttpMethod.PATCH, "/v1/api/roles/" + ID + "/reativar", "ROLE_GERENCIAR_PERMISSOES"),
                alvo(HttpMethod.PATCH, "/v1/api/roles/" + ID + "/aprovar", "ROLE_APROVAR"),
                alvo(HttpMethod.PATCH, "/v1/api/roles/" + ID + "/reprovar", "ROLE_APROVAR"),
                alvo(HttpMethod.GET, "/v1/api/roles/" + ID + "/permissoes", "ROLE_GERENCIAR_PERMISSOES"),
                // fila e decisão de afastamento
                alvo(HttpMethod.GET, "/v1/api/solicitacoes-afastamento", "CORISTA_APROVAR_AFASTAMENTO"),
                alvo(HttpMethod.PATCH, "/v1/api/solicitacoes-afastamento/" + ID + "/aprovar", "CORISTA_APROVAR_AFASTAMENTO"),
                alvo(HttpMethod.PATCH, "/v1/api/solicitacoes-afastamento/" + ID + "/negar", "CORISTA_APROVAR_AFASTAMENTO"));
    }

    private static Arguments alvo(HttpMethod metodo, String caminho, String permissao) {
        return Arguments.of(metodo, caminho, permissao);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requisicao(HttpMethod metodo, String caminho) {
        return MockMvcRequestBuilders.request(metodo, caminho);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor logadoComPermissoes(String... permissoes) {
        return SecurityMockMvcRequestPostProcessors.securityContext(
                UsuarioTesteFactory.contexto(new String[]{"PAPEL_SEM_PODER"}, false, permissoes));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("endpoints")
    void semLogin_retorna401(HttpMethod metodo, String caminho, String permissao) throws Exception {
        mockMvc.perform(requisicao(metodo, caminho))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("endpoints")
    void logadoSemAPermissao_retorna403(HttpMethod metodo, String caminho, String permissao) throws Exception {
        mockMvc.perform(requisicao(metodo, caminho).with(logadoComPermissoes("PERMISSAO_QUE_NAO_E_A_DO_ENDPOINT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("endpoints")
    void logadoComAPermissao_passaPelaSeguranca(HttpMethod metodo, String caminho, String permissao) throws Exception {
        int statusDaResposta = mockMvc.perform(requisicao(metodo, caminho).with(logadoComPermissoes(permissao)))
                .andReturn().getResponse().getStatus();

        assertThat(statusDaResposta).isNotIn(401, 403);
    }
}
