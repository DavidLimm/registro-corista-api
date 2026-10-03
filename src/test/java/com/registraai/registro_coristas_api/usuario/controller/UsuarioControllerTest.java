package com.registraai.registro_coristas_api.usuario.controller;

import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.pessoa.model.StatusPessoa;
import com.registraai.registro_coristas_api.role.exception.RoleInativaException;
import com.registraai.registro_coristas_api.role.exception.RoleNaoEncontradaException;
import com.registraai.registro_coristas_api.role.model.Role;
import com.registraai.registro_coristas_api.usuario.dto.UsuarioRequest;
import com.registraai.registro_coristas_api.usuario.exception.UsuarioNaoEncontradoException;
import com.registraai.registro_coristas_api.usuario.exception.UsuarioPessoaImutavelException;
import com.registraai.registro_coristas_api.usuario.exception.EmailDuplicadoException;
import com.registraai.registro_coristas_api.usuario.exception.PessoaJaPossuiUsuarioException;
import com.registraai.registro_coristas_api.usuario.model.Usuario;
import com.registraai.registro_coristas_api.usuario.service.UsuarioService;
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

@WebMvcTest(UsuarioController.class)
class UsuarioControllerTest {

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
    private UsuarioService usuarioService;

    private Usuario usuarioPersistido(UUID id) {
        Instant agora = Instant.parse("2026-09-20T12:00:00Z");
        Pessoa pessoa = Pessoa.builder().id(PESSOA_ID).nome("Maria da Silva").status(StatusPessoa.APROVADO).build();
        Role role = Role.builder().id(ROLE_ID).nome("CORISTA_JOVENS").descricao("Corista jovem").ativo(true).build();
        return Usuario.builder().id(id).pessoa(pessoa).email("maria@exemplo.com").senhaHash("hash")
                .ativo(true).roles(Set.of(role)).criadoEm(agora).atualizadoEm(agora).build();
    }

    // ---------- criar ----------

    @Test
    void criar_retorna201ComLocationENuncaExpoeSenha() throws Exception {
        UUID id = UUID.randomUUID();
        when(usuarioService.criar(any(UsuarioRequest.class))).thenReturn(usuarioPersistido(id));

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

        verifyNoInteractions(usuarioService);
    }

    @Test
    void criar_emailInvalido_retorna400() throws Exception {
        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON).content("""
                        { "pessoaId": "22222222-2222-2222-2222-222222222222", "email": "nao-e-email",
                          "senha": "senhaForte123", "roleIds": ["33333333-3333-3333-3333-333333333333"] }
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(usuarioService);
    }

    @Test
    void criar_senhaCurta_retorna400() throws Exception {
        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON).content("""
                        { "pessoaId": "22222222-2222-2222-2222-222222222222", "email": "a@a.com",
                          "senha": "curta", "roleIds": ["33333333-3333-3333-3333-333333333333"] }
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(usuarioService);
    }

    @Test
    void criar_roleIdsVazio_retorna400() throws Exception {
        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON).content("""
                        { "pessoaId": "22222222-2222-2222-2222-222222222222", "email": "a@a.com",
                          "senha": "senhaForte123", "roleIds": [] }
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(usuarioService);
    }

    @Test
    void criar_roleIdsComElementoNulo_retorna400ENaoChamaService() throws Exception {
        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON).content("""
                        { "pessoaId": "22222222-2222-2222-2222-222222222222", "email": "a@a.com",
                          "senha": "senhaForte123", "roleIds": ["33333333-3333-3333-3333-333333333333", null] }
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(usuarioService);
    }

    @Test
    void criar_pessoaJaTemUsuario_retorna409() throws Exception {
        when(usuarioService.criar(any(UsuarioRequest.class))).thenThrow(new PessoaJaPossuiUsuarioException(PESSOA_ID));

        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict());
    }

    @Test
    void criar_emailDuplicado_retorna409() throws Exception {
        when(usuarioService.criar(any(UsuarioRequest.class))).thenThrow(new EmailDuplicadoException("maria@exemplo.com"));

        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict());
    }

    @Test
    void criar_roleInexistente_retorna404() throws Exception {
        when(usuarioService.criar(any(UsuarioRequest.class))).thenThrow(new RoleNaoEncontradaException(ROLE_ID));

        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isNotFound());
    }

    @Test
    void criar_roleInativa_retorna409() throws Exception {
        when(usuarioService.criar(any(UsuarioRequest.class))).thenThrow(new RoleInativaException("CORISTA_JOVENS"));

        mockMvc.perform(post("/v1/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict());
    }

    // ---------- listar ----------

    @Test
    void listar_repassaFiltroEPaginacaoEDevolveOsMetadadosDaPagina() throws Exception {
        UUID id = UUID.randomUUID();
        var pagina = new PageImpl<>(List.of(usuarioPersistido(id)), PageRequest.of(1, 5), 11);
        when(usuarioService.listar(true, 1, 5)).thenReturn(pagina);

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
        when(usuarioService.listar(null, 0, 20)).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/v1/api/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        verify(usuarioService).listar(null, 0, 20);
    }

    @Test
    void listar_comParametrosInvalidos_retorna400ENaoChamaService() throws Exception {
        mockMvc.perform(get("/v1/api/usuarios").param("size", "101")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/api/usuarios").param("size", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/api/usuarios").param("page", "-1")).andExpect(status().isBadRequest());

        verifyNoInteractions(usuarioService);
    }

    // ---------- buscar / atualizar / inativar / reativar ----------

    @Test
    void buscarPorId_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(usuarioService.buscarPorId(id)).thenReturn(usuarioPersistido(id));

        mockMvc.perform(get("/v1/api/usuarios/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("maria@exemplo.com"));
    }

    @Test
    void buscarPorId_inexistente_retorna404ComProblemDetails() throws Exception {
        UUID id = UUID.randomUUID();
        when(usuarioService.buscarPorId(id)).thenThrow(new UsuarioNaoEncontradoException(id));

        mockMvc.perform(get("/v1/api/usuarios/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Usuário não encontrado: " + id));
    }

    @Test
    void atualizar_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(usuarioService.atualizar(eq(id), any(UsuarioRequest.class))).thenReturn(usuarioPersistido(id));

        mockMvc.perform(put("/v1/api/usuarios/{id}", id).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void atualizar_pessoaImutavel_retorna409() throws Exception {
        UUID id = UUID.randomUUID();
        when(usuarioService.atualizar(eq(id), any(UsuarioRequest.class))).thenThrow(new UsuarioPessoaImutavelException());

        mockMvc.perform(put("/v1/api/usuarios/{id}", id).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict());
    }

    @Test
    void atualizar_inexistente_retorna404() throws Exception {
        UUID id = UUID.randomUUID();
        when(usuarioService.atualizar(eq(id), any(UsuarioRequest.class))).thenThrow(new UsuarioNaoEncontradoException(id));

        mockMvc.perform(put("/v1/api/usuarios/{id}", id).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isNotFound());
    }

    @Test
    void inativar_retorna204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/v1/api/usuarios/{id}", id)).andExpect(status().isNoContent());

        verify(usuarioService).inativar(id);
    }

    @Test
    void inativar_inexistente_retorna404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new UsuarioNaoEncontradoException(id)).when(usuarioService).inativar(id);

        mockMvc.perform(delete("/v1/api/usuarios/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void reativar_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(usuarioService.reativar(id)).thenReturn(usuarioPersistido(id));

        mockMvc.perform(patch("/v1/api/usuarios/{id}/reativar", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));
    }
}
