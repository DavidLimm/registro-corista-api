package com.registraai.registro_coristas_api.usuario.controller;

import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.pessoa.model.StatusPessoa;
import com.registraai.registro_coristas_api.role.exception.RoleInativaException;
import com.registraai.registro_coristas_api.role.exception.RoleNaoEncontradaException;
import com.registraai.registro_coristas_api.role.model.Role;
import com.registraai.registro_coristas_api.usuario.dto.AppUserRequest;
import com.registraai.registro_coristas_api.usuario.exception.AppUserNaoEncontradoException;
import com.registraai.registro_coristas_api.usuario.exception.AppUserPessoaImutavelException;
import com.registraai.registro_coristas_api.usuario.exception.EmailDuplicadoException;
import com.registraai.registro_coristas_api.usuario.exception.PessoaJaPossuiUsuarioException;
import com.registraai.registro_coristas_api.usuario.model.AppUser;
import com.registraai.registro_coristas_api.usuario.service.AppUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppUserController.class)
class AppUserControllerTest {

    private static final UUID PESSOA_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ROLE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final String JSON_VALIDO = """
            {
              "pessoaId": "22222222-2222-2222-2222-222222222222",
              "email": "maria@exemplo.com",
              "senha": "senhaForte123",
              "roleIds": ["33333333-3333-3333-3333-333333333333"]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppUserService appUserService;

    private AppUser appUserPersistido(UUID id) {
        Instant agora = Instant.parse("2026-09-20T12:00:00Z");
        Pessoa pessoa = Pessoa.builder().id(PESSOA_ID).nome("Maria da Silva").status(StatusPessoa.APROVADO).build();
        Role role = Role.builder().id(ROLE_ID).nome("CORISTA_JOVENS").descricao("Corista jovem").ativo(true).build();
        return AppUser.builder().id(id).pessoa(pessoa).email("maria@exemplo.com").senhaHash("hash")
                .ativo(true).roles(Set.of(role)).criadoEm(agora).atualizadoEm(agora).build();
    }

    // ---------- criar ----------

    @Test
    void criar_retorna201ComLocationENuncaExpoeSenha() throws Exception {
        UUID id = UUID.randomUUID();
        when(appUserService.criar(any(AppUserRequest.class))).thenReturn(appUserPersistido(id));

        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/v1/api/usuarios/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.pessoaId").value(PESSOA_ID.toString()))
                .andExpect(jsonPath("$.pessoaNome").value("Maria da Silva"))
                .andExpect(jsonPath("$.email").value("maria@exemplo.com"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.roles[0]").value("CORISTA_JOVENS"))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
    }

    @Test
    void criar_semCampoObrigatorio_retorna400ENaoChamaService() throws Exception {
        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(appUserService);
    }

    @Test
    void criar_emailInvalido_retorna400() throws Exception {
        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON).content("""
                        { "pessoaId": "22222222-2222-2222-2222-222222222222", "email": "nao-e-email",
                          "senha": "senhaForte123", "roleIds": ["33333333-3333-3333-3333-333333333333"] }
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(appUserService);
    }

    @Test
    void criar_senhaCurta_retorna400() throws Exception {
        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON).content("""
                        { "pessoaId": "22222222-2222-2222-2222-222222222222", "email": "a@a.com",
                          "senha": "curta", "roleIds": ["33333333-3333-3333-3333-333333333333"] }
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(appUserService);
    }

    @Test
    void criar_roleIdsVazio_retorna400() throws Exception {
        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON).content("""
                        { "pessoaId": "22222222-2222-2222-2222-222222222222", "email": "a@a.com",
                          "senha": "senhaForte123", "roleIds": [] }
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(appUserService);
    }

    @Test
    void criar_roleIdsComElementoNulo_retorna400ENaoChamaService() throws Exception {
        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON).content("""
                        { "pessoaId": "22222222-2222-2222-2222-222222222222", "email": "a@a.com",
                          "senha": "senhaForte123", "roleIds": ["33333333-3333-3333-3333-333333333333", null] }
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(appUserService);
    }

    @Test
    void criar_pessoaJaTemUsuario_retorna409() throws Exception {
        when(appUserService.criar(any(AppUserRequest.class))).thenThrow(new PessoaJaPossuiUsuarioException(PESSOA_ID));

        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict());
    }

    @Test
    void criar_emailDuplicado_retorna409() throws Exception {
        when(appUserService.criar(any(AppUserRequest.class))).thenThrow(new EmailDuplicadoException("maria@exemplo.com"));

        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict());
    }

    @Test
    void criar_roleInexistente_retorna404() throws Exception {
        when(appUserService.criar(any(AppUserRequest.class))).thenThrow(new RoleNaoEncontradaException(ROLE_ID));

        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isNotFound());
    }

    @Test
    void criar_roleInativa_retorna409() throws Exception {
        when(appUserService.criar(any(AppUserRequest.class))).thenThrow(new RoleInativaException("CORISTA_JOVENS"));

        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict());
    }

    // ---------- listar ----------

    @Test
    void listar_repassaFiltroEPaginacaoEDevolveOsMetadadosDaPagina() throws Exception {
        UUID id = UUID.randomUUID();
        var pagina = new PageImpl<>(List.of(appUserPersistido(id)), PageRequest.of(1, 5), 11);
        when(appUserService.listar(true, 1, 5)).thenReturn(pagina);

        mockMvc.perform(get("/v1/api/usuarios").param("ativo", "true").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(5))
                .andExpect(jsonPath("$.page.totalElements").value(11))
                .andExpect(jsonPath("$.page.totalPages").value(3));
    }

    @Test
    void listar_semParametros_usaPaginaZeroTamanhoVinteESemFiltro() throws Exception {
        when(appUserService.listar(null, 0, 20)).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/v1/api/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        verify(appUserService).listar(null, 0, 20);
    }

    @Test
    void listar_comParametrosInvalidos_retorna400ENaoChamaService() throws Exception {
        mockMvc.perform(get("/v1/api/usuarios").param("size", "101")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/api/usuarios").param("size", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/api/usuarios").param("page", "-1")).andExpect(status().isBadRequest());

        verifyNoInteractions(appUserService);
    }

    // ---------- buscar / atualizar / inativar / reativar ----------

    @Test
    void buscarPorId_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(appUserService.buscarPorId(id)).thenReturn(appUserPersistido(id));

        mockMvc.perform(get("/v1/api/usuarios/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("maria@exemplo.com"));
    }

    @Test
    void buscarPorId_inexistente_retorna404ComProblemDetails() throws Exception {
        UUID id = UUID.randomUUID();
        when(appUserService.buscarPorId(id)).thenThrow(new AppUserNaoEncontradoException(id));

        mockMvc.perform(get("/v1/api/usuarios/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Usuário não encontrado: " + id));
    }

    @Test
    void atualizar_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(appUserService.atualizar(eq(id), any(AppUserRequest.class))).thenReturn(appUserPersistido(id));

        mockMvc.perform(put("/v1/api/usuarios/{id}", id).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void atualizar_pessoaImutavel_retorna409() throws Exception {
        UUID id = UUID.randomUUID();
        when(appUserService.atualizar(eq(id), any(AppUserRequest.class))).thenThrow(new AppUserPessoaImutavelException());

        mockMvc.perform(put("/v1/api/usuarios/{id}", id).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict());
    }

    @Test
    void atualizar_inexistente_retorna404() throws Exception {
        UUID id = UUID.randomUUID();
        when(appUserService.atualizar(eq(id), any(AppUserRequest.class))).thenThrow(new AppUserNaoEncontradoException(id));

        mockMvc.perform(put("/v1/api/usuarios/{id}", id).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isNotFound());
    }

    @Test
    void inativar_retorna204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/v1/api/usuarios/{id}", id)).andExpect(status().isNoContent());

        verify(appUserService).inativar(id);
    }

    @Test
    void inativar_inexistente_retorna404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new AppUserNaoEncontradoException(id)).when(appUserService).inativar(id);

        mockMvc.perform(delete("/v1/api/usuarios/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void reativar_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(appUserService.reativar(id)).thenReturn(appUserPersistido(id));

        mockMvc.perform(patch("/v1/api/usuarios/{id}/reativar", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));
    }
}
