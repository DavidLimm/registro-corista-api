package com.registraai.registro_coristas_api.usuario.service;

import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.pessoa.model.StatusPessoa;
import com.registraai.registro_coristas_api.pessoa.service.PessoaService;
import com.registraai.registro_coristas_api.role.exception.RoleInativaException;
import com.registraai.registro_coristas_api.role.exception.RoleNaoEncontradaException;
import com.registraai.registro_coristas_api.role.model.Role;
import com.registraai.registro_coristas_api.role.repository.RoleRepository;
import com.registraai.registro_coristas_api.usuario.dto.AppUserRequest;
import com.registraai.registro_coristas_api.usuario.exception.AppUserNaoEncontradoException;
import com.registraai.registro_coristas_api.usuario.exception.AppUserPessoaImutavelException;
import com.registraai.registro_coristas_api.usuario.exception.EmailDuplicadoException;
import com.registraai.registro_coristas_api.usuario.exception.PessoaJaPossuiUsuarioException;
import com.registraai.registro_coristas_api.usuario.exception.PessoaNaoAprovadaException;
import com.registraai.registro_coristas_api.usuario.model.AppUser;
import com.registraai.registro_coristas_api.usuario.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PessoaService pessoaService;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AppUserService appUserService;

    @BeforeEach
    void criarService() {
        appUserService = new AppUserService(appUserRepository, pessoaService, roleRepository, passwordEncoder);
    }

    private Pessoa pessoaAprovada() {
        return Pessoa.builder().id(UUID.randomUUID()).nome("Maria").status(StatusPessoa.APROVADO).build();
    }

    private Role role(String nome, boolean ativo) {
        return Role.builder().id(UUID.randomUUID()).nome(nome).descricao(nome).ativo(ativo).build();
    }

    private AppUserRequest request(UUID pessoaId, String email, Set<UUID> roleIds) {
        return new AppUserRequest(pessoaId, email, "senhaForte123", roleIds);
    }

    private void repositorioDevolveOQueRecebe() {
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    // ---------- criar ----------

    @Test
    void criar_sucesso_gravaEmailNormalizadoESenhaComHash() {
        Pessoa pessoa = pessoaAprovada();
        Role role = role("CORISTA_JOVENS", true);
        when(pessoaService.buscarPorId(pessoa.getId())).thenReturn(pessoa);
        when(appUserRepository.existsByPessoaId(pessoa.getId())).thenReturn(false);
        when(appUserRepository.existsByEmail("maria@exemplo.com")).thenReturn(false);
        when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("senhaForte123")).thenReturn("hash-bcrypt");
        repositorioDevolveOQueRecebe();

        AppUser appUser = appUserService.criar(request(pessoa.getId(), "  Maria@Exemplo.com ", Set.of(role.getId())));

        assertThat(appUser.getPessoa()).isSameAs(pessoa);
        assertThat(appUser.getEmail()).isEqualTo("maria@exemplo.com");
        assertThat(appUser.getSenhaHash()).isEqualTo("hash-bcrypt");
        assertThat(appUser.getRoles()).containsExactly(role);
        assertThat(appUser.isAtivo()).isTrue();
    }

    @Test
    void criar_pessoaNaoAprovada_lancaExcecao() {
        Pessoa pendente = Pessoa.builder().id(UUID.randomUUID()).status(StatusPessoa.PENDENTE).build();
        when(pessoaService.buscarPorId(pendente.getId())).thenReturn(pendente);

        assertThatThrownBy(() -> appUserService.criar(request(pendente.getId(), "a@a.com", Set.of(UUID.randomUUID()))))
                .isInstanceOf(PessoaNaoAprovadaException.class);
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void criar_pessoaJaTemUsuario_lancaExcecao() {
        Pessoa pessoa = pessoaAprovada();
        when(pessoaService.buscarPorId(pessoa.getId())).thenReturn(pessoa);
        when(appUserRepository.existsByPessoaId(pessoa.getId())).thenReturn(true);

        assertThatThrownBy(() -> appUserService.criar(request(pessoa.getId(), "a@a.com", Set.of(UUID.randomUUID()))))
                .isInstanceOf(PessoaJaPossuiUsuarioException.class);
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void criar_emailDuplicado_lancaExcecao() {
        Pessoa pessoa = pessoaAprovada();
        when(pessoaService.buscarPorId(pessoa.getId())).thenReturn(pessoa);
        when(appUserRepository.existsByPessoaId(pessoa.getId())).thenReturn(false);
        when(appUserRepository.existsByEmail("a@a.com")).thenReturn(true);

        assertThatThrownBy(() -> appUserService.criar(request(pessoa.getId(), "a@a.com", Set.of(UUID.randomUUID()))))
                .isInstanceOf(EmailDuplicadoException.class);
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void criar_roleInexistente_lancaExcecao() {
        Pessoa pessoa = pessoaAprovada();
        UUID roleId = UUID.randomUUID();
        when(pessoaService.buscarPorId(pessoa.getId())).thenReturn(pessoa);
        when(appUserRepository.existsByPessoaId(pessoa.getId())).thenReturn(false);
        when(appUserRepository.existsByEmail("a@a.com")).thenReturn(false);
        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appUserService.criar(request(pessoa.getId(), "a@a.com", Set.of(roleId))))
                .isInstanceOf(RoleNaoEncontradaException.class);
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void criar_roleInativa_lancaExcecao() {
        Pessoa pessoa = pessoaAprovada();
        Role inativa = role("APOIO_JOVENS", false);
        when(pessoaService.buscarPorId(pessoa.getId())).thenReturn(pessoa);
        when(appUserRepository.existsByPessoaId(pessoa.getId())).thenReturn(false);
        when(appUserRepository.existsByEmail("a@a.com")).thenReturn(false);
        when(roleRepository.findById(inativa.getId())).thenReturn(Optional.of(inativa));

        assertThatThrownBy(() -> appUserService.criar(request(pessoa.getId(), "a@a.com", Set.of(inativa.getId()))))
                .isInstanceOf(RoleInativaException.class);
        verify(appUserRepository, never()).save(any());
    }

    // ---------- buscar / listar ----------

    @Test
    void buscarPorId_retornaUsuarioExistente() {
        UUID id = UUID.randomUUID();
        AppUser existente = AppUser.builder().id(id).build();
        when(appUserRepository.findById(id)).thenReturn(Optional.of(existente));

        assertThat(appUserService.buscarPorId(id)).isSameAs(existente);
    }

    @Test
    void buscarPorId_lancaExcecaoQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(appUserRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appUserService.buscarPorId(id)).isInstanceOf(AppUserNaoEncontradoException.class);
    }

    @Test
    void listar_semFiltro_usaListaCompleta() {
        List<AppUser> todos = List.of(AppUser.builder().build());
        when(appUserRepository.findAllByOrderByEmailAsc()).thenReturn(todos);

        assertThat(appUserService.listar(null)).isSameAs(todos);
    }

    @Test
    void listar_comFiltro_delegaParaORepositorio() {
        List<AppUser> ativos = List.of(AppUser.builder().build());
        when(appUserRepository.findAllByAtivoOrderByEmailAsc(true)).thenReturn(ativos);

        assertThat(appUserService.listar(true)).isSameAs(ativos);
    }

    // ---------- atualizar ----------

    @Test
    void atualizar_sucesso_trocaEmailERoles() {
        UUID id = UUID.randomUUID();
        Pessoa pessoa = pessoaAprovada();
        Role novaRole = role("MAESTRO_JOVENS", true);
        AppUser existente = AppUser.builder().id(id).pessoa(pessoa).email("antigo@a.com").build();
        when(appUserRepository.findById(id)).thenReturn(Optional.of(existente));
        when(appUserRepository.existsByEmailAndIdNot("novo@a.com", id)).thenReturn(false);
        when(roleRepository.findById(novaRole.getId())).thenReturn(Optional.of(novaRole));
        when(passwordEncoder.encode("senhaForte123")).thenReturn("novo-hash");
        repositorioDevolveOQueRecebe();

        AppUser atualizado = appUserService.atualizar(id, request(pessoa.getId(), "novo@a.com", Set.of(novaRole.getId())));

        assertThat(atualizado.getEmail()).isEqualTo("novo@a.com");
        assertThat(atualizado.getSenhaHash()).isEqualTo("novo-hash");
        assertThat(atualizado.getRoles()).containsExactly(novaRole);
    }

    @Test
    void atualizar_pessoaDiferenteDaOriginal_lancaExcecao() {
        UUID id = UUID.randomUUID();
        Pessoa pessoaOriginal = pessoaAprovada();
        AppUser existente = AppUser.builder().id(id).pessoa(pessoaOriginal).build();
        when(appUserRepository.findById(id)).thenReturn(Optional.of(existente));

        UUID outraPessoaId = UUID.randomUUID();
        assertThatThrownBy(() -> appUserService.atualizar(id, request(outraPessoaId, "a@a.com", Set.of(UUID.randomUUID()))))
                .isInstanceOf(AppUserPessoaImutavelException.class);
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void atualizar_emailDuplicado_lancaExcecao() {
        UUID id = UUID.randomUUID();
        Pessoa pessoa = pessoaAprovada();
        AppUser existente = AppUser.builder().id(id).pessoa(pessoa).build();
        when(appUserRepository.findById(id)).thenReturn(Optional.of(existente));
        when(appUserRepository.existsByEmailAndIdNot("ocupado@a.com", id)).thenReturn(true);

        assertThatThrownBy(() -> appUserService.atualizar(id, request(pessoa.getId(), "ocupado@a.com", Set.of(UUID.randomUUID()))))
                .isInstanceOf(EmailDuplicadoException.class);
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void atualizar_lancaExcecaoQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(appUserRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appUserService.atualizar(id, request(UUID.randomUUID(), "a@a.com", Set.of(UUID.randomUUID()))))
                .isInstanceOf(AppUserNaoEncontradoException.class);
        verify(appUserRepository, never()).save(any());
    }

    // ---------- inativar / reativar ----------

    @Test
    void inativar_marcaComoInativo() {
        UUID id = UUID.randomUUID();
        AppUser existente = AppUser.builder().id(id).ativo(true).build();
        when(appUserRepository.findById(id)).thenReturn(Optional.of(existente));

        appUserService.inativar(id);

        assertThat(existente.isAtivo()).isFalse();
        verify(appUserRepository).save(existente);
    }

    @Test
    void inativar_lancaExcecaoQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(appUserRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appUserService.inativar(id)).isInstanceOf(AppUserNaoEncontradoException.class);
    }

    @Test
    void reativar_marcaComoAtivo() {
        UUID id = UUID.randomUUID();
        AppUser existente = AppUser.builder().id(id).ativo(false).build();
        when(appUserRepository.findById(id)).thenReturn(Optional.of(existente));
        repositorioDevolveOQueRecebe();

        AppUser reativado = appUserService.reativar(id);

        assertThat(reativado.isAtivo()).isTrue();
    }
}
