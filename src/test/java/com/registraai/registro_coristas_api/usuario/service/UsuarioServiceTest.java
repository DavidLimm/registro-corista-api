package com.registraai.registro_coristas_api.usuario.service;

import static org.mockito.Mockito.verifyNoInteractions;
import com.registraai.registro_coristas_api.autenticacao.service.UsuarioLogado;
import com.registraai.registro_coristas_api.usuario.event.PessoaVirouLiderEvent;
import com.registraai.registro_coristas_api.usuario.exception.OperacaoReservadaAoAdminException;
import com.registraai.registro_coristas_api.usuario.exception.PapelCoristaExclusivoException;
import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.pessoa.model.StatusPessoa;
import com.registraai.registro_coristas_api.pessoa.service.PessoaService;
import com.registraai.registro_coristas_api.role.exception.RoleInativaException;
import com.registraai.registro_coristas_api.role.exception.RoleNaoEncontradaException;
import com.registraai.registro_coristas_api.role.model.Role;
import com.registraai.registro_coristas_api.role.repository.RoleRepository;
import com.registraai.registro_coristas_api.usuario.dto.UsuarioRequest;
import com.registraai.registro_coristas_api.usuario.exception.UsuarioNaoEncontradoException;
import com.registraai.registro_coristas_api.usuario.exception.UsuarioPessoaImutavelException;
import com.registraai.registro_coristas_api.usuario.exception.EmailDuplicadoException;
import com.registraai.registro_coristas_api.usuario.exception.PessoaJaPossuiUsuarioException;
import com.registraai.registro_coristas_api.usuario.model.Usuario;
import com.registraai.registro_coristas_api.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private ApplicationEventPublisher eventos;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PessoaService pessoaService;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UsuarioLogado usuarioLogado;

    @Mock
    private EscopoService escopoService;

    @Mock
    private EscopoDeAcesso escopo;

    private UsuarioService usuarioService;

    @BeforeEach
    void semRestricaoDeEscopo() {
        // o recorte por agrupamento tem testes próprios (EscopoDeAcessoTest); aqui ninguém é barrado por ele
        org.mockito.Mockito.lenient().when(escopo.restringirA(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocacao -> org.springframework.data.jpa.domain.Specification.unrestricted());
    }

    @BeforeEach
    void criarService() {
        usuarioService = new UsuarioService(usuarioRepository, pessoaService, roleRepository, passwordEncoder, eventos, usuarioLogado, escopoService, escopo);
    }

    private Pessoa pessoaAprovada() {
        return Pessoa.builder().id(UUID.randomUUID()).nome("Maria").status(StatusPessoa.APROVADO).build();
    }

    private Role role(String nome, boolean ativo) {
        return Role.builder().id(UUID.randomUUID()).nome(nome).descricao(nome).ativo(ativo).build();
    }

    private UsuarioRequest request(UUID pessoaId, String email, Set<UUID> roleIds) {
        return new UsuarioRequest(pessoaId, email, "SenhaForte@123", roleIds);
    }

    private void repositorioDevolveOQueRecebe() {
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    // ---------- criar ----------

    @Test
    void criar_sucesso_gravaEmailNormalizadoESenhaComHash() {
        Pessoa pessoa = pessoaAprovada();
        Role role = role("CORISTA_JOVENS", true);
        when(pessoaService.buscarPorId(pessoa.getId())).thenReturn(pessoa);
        when(usuarioRepository.existsByPessoaId(pessoa.getId())).thenReturn(false);
        when(usuarioRepository.existsByEmail("maria@exemplo.com")).thenReturn(false);
        when(roleRepository.findAllById(Set.of(role.getId()))).thenReturn(List.of(role));
        when(passwordEncoder.encode("SenhaForte@123")).thenReturn("hash-bcrypt");
        repositorioDevolveOQueRecebe();

        Usuario usuario = usuarioService.criar(request(pessoa.getId(), "  Maria@Exemplo.com ", Set.of(role.getId())));

        assertThat(usuario.getPessoa()).isSameAs(pessoa);
        assertThat(usuario.getEmail()).isEqualTo("maria@exemplo.com");
        assertThat(usuario.getSenhaHash()).isEqualTo("hash-bcrypt");
        assertThat(usuario.getRoles()).containsExactly(role);
        assertThat(usuario.isAtivo()).isTrue();
    }

    @Test
    void criar_pessoaPendente_epermitido_naoExigeAprovacao() {
        // auto-cadastro cria pessoa e usuário juntos, ainda PENDENTE (ver CoristaService.criar)
        Pessoa pendente = Pessoa.builder().id(UUID.randomUUID()).status(StatusPessoa.PENDENTE).build();
        Role role = role("CORISTA_JOVENS", true);
        when(pessoaService.buscarPorId(pendente.getId())).thenReturn(pendente);
        when(usuarioRepository.existsByPessoaId(pendente.getId())).thenReturn(false);
        when(usuarioRepository.existsByEmail("a@a.com")).thenReturn(false);
        when(roleRepository.findAllById(Set.of(role.getId()))).thenReturn(List.of(role));
        repositorioDevolveOQueRecebe();

        assertThat(usuarioService.criar(request(pendente.getId(), "a@a.com", Set.of(role.getId()))).getPessoa())
                .isSameAs(pendente);
    }

    @Test
    void criar_pessoaJaTemUsuario_lancaExcecao() {
        Pessoa pessoa = pessoaAprovada();
        when(pessoaService.buscarPorId(pessoa.getId())).thenReturn(pessoa);
        when(usuarioRepository.existsByPessoaId(pessoa.getId())).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.criar(request(pessoa.getId(), "a@a.com", Set.of(UUID.randomUUID()))))
                .isInstanceOf(PessoaJaPossuiUsuarioException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void criar_emailDuplicado_lancaExcecao() {
        Pessoa pessoa = pessoaAprovada();
        when(pessoaService.buscarPorId(pessoa.getId())).thenReturn(pessoa);
        when(usuarioRepository.existsByPessoaId(pessoa.getId())).thenReturn(false);
        when(usuarioRepository.existsByEmail("a@a.com")).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.criar(request(pessoa.getId(), "a@a.com", Set.of(UUID.randomUUID()))))
                .isInstanceOf(EmailDuplicadoException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void criar_roleInexistente_lancaExcecao() {
        Pessoa pessoa = pessoaAprovada();
        UUID roleId = UUID.randomUUID();
        when(pessoaService.buscarPorId(pessoa.getId())).thenReturn(pessoa);
        when(usuarioRepository.existsByPessoaId(pessoa.getId())).thenReturn(false);
        when(usuarioRepository.existsByEmail("a@a.com")).thenReturn(false);
        when(roleRepository.findAllById(Set.of(roleId))).thenReturn(List.of());

        assertThatThrownBy(() -> usuarioService.criar(request(pessoa.getId(), "a@a.com", Set.of(roleId))))
                .isInstanceOf(RoleNaoEncontradaException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void criar_roleInativa_lancaExcecao() {
        Pessoa pessoa = pessoaAprovada();
        Role inativa = role("APOIO_JOVENS", false);
        when(pessoaService.buscarPorId(pessoa.getId())).thenReturn(pessoa);
        when(usuarioRepository.existsByPessoaId(pessoa.getId())).thenReturn(false);
        when(usuarioRepository.existsByEmail("a@a.com")).thenReturn(false);
        when(roleRepository.findAllById(Set.of(inativa.getId()))).thenReturn(List.of(inativa));

        assertThatThrownBy(() -> usuarioService.criar(request(pessoa.getId(), "a@a.com", Set.of(inativa.getId()))))
                .isInstanceOf(RoleInativaException.class);
        verify(usuarioRepository, never()).save(any());
    }

    // ---------- criarComPapelUnico (auto-cadastro) ----------

    @Test
    void criarComPapelUnico_resolveORoleUnicoPeloNome() {
        Pessoa pessoa = pessoaAprovada();
        Role role = role("CORISTA_ADOLESCENTES", true);
        when(usuarioRepository.existsByPessoaId(pessoa.getId())).thenReturn(false);
        when(usuarioRepository.existsByEmail("ana@exemplo.com")).thenReturn(false);
        when(roleRepository.findByNome("CORISTA_ADOLESCENTES")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("SenhaForte@123")).thenReturn("hash");
        repositorioDevolveOQueRecebe();

        Usuario usuario = usuarioService.criarComPapelUnico(pessoa, "ana@exemplo.com", "SenhaForte@123", "CORISTA_ADOLESCENTES");

        assertThat(usuario.getRoles()).containsExactly(role);
        assertThat(usuario.getEmail()).isEqualTo("ana@exemplo.com");
    }

    @Test
    void criarComPapelUnico_roleInativa_lancaExcecao() {
        Pessoa pessoa = pessoaAprovada();
        Role inativa = role("CORISTA_JOVENS", false);
        when(usuarioRepository.existsByPessoaId(pessoa.getId())).thenReturn(false);
        when(usuarioRepository.existsByEmail("ana@exemplo.com")).thenReturn(false);
        when(roleRepository.findByNome("CORISTA_JOVENS")).thenReturn(Optional.of(inativa));

        assertThatThrownBy(() -> usuarioService.criarComPapelUnico(pessoa, "ana@exemplo.com", "SenhaForte@123", "CORISTA_JOVENS"))
                .isInstanceOf(RoleInativaException.class);
        verify(usuarioRepository, never()).save(any());
    }

    // ---------- buscar / listar ----------

    @Test
    void buscarPorId_retornaUsuarioExistente() {
        UUID id = UUID.randomUUID();
        Usuario existente = Usuario.builder().id(id).build();
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(existente));

        assertThat(usuarioService.buscarPorId(id)).isSameAs(existente);
    }

    @Test
    void buscarPorId_lancaExcecaoQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.buscarPorId(id)).isInstanceOf(UsuarioNaoEncontradoException.class);
    }

    @Test
    void listar_paginaOrdenandoPorEmailComDesempatePorId() {
        Page<Usuario> pagina = new PageImpl<>(List.of(Usuario.builder().build()));
        Pageable esperado = PageRequest.of(2, 15, Sort.by("email").and(Sort.by("id")));
        when(usuarioRepository.findAll(ArgumentMatchers.<Specification<Usuario>>any(), eq(esperado))).thenReturn(pagina);

        assertThat(usuarioService.listar(null, 2, 15)).isSameAs(pagina);
    }

    @Test
    void listar_comFiltroAtivo_delegaParaORepositorio() {
        Page<Usuario> pagina = new PageImpl<>(List.of(Usuario.builder().build()));
        Pageable esperado = PageRequest.of(0, 20, Sort.by("email").and(Sort.by("id")));
        when(usuarioRepository.findAll(ArgumentMatchers.<Specification<Usuario>>any(), eq(esperado))).thenReturn(pagina);

        assertThat(usuarioService.listar(true, 0, 20)).isSameAs(pagina);
    }

    // ---------- atualizar ----------

    @Test
    void atualizar_sucesso_trocaEmailERoles() {
        UUID id = UUID.randomUUID();
        Pessoa pessoa = pessoaAprovada();
        Role novaRole = role("MAESTRO_JOVENS", true);
        Usuario existente = Usuario.builder().id(id).pessoa(pessoa).email("antigo@a.com").build();
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(existente));
        when(usuarioRepository.existsByEmailAndIdNot("novo@a.com", id)).thenReturn(false);
        when(roleRepository.findAllById(Set.of(novaRole.getId()))).thenReturn(List.of(novaRole));
        when(passwordEncoder.encode("SenhaForte@123")).thenReturn("novo-hash");
        repositorioDevolveOQueRecebe();

        Usuario atualizado = usuarioService.atualizar(id, request(pessoa.getId(), "novo@a.com", Set.of(novaRole.getId())));

        assertThat(atualizado.getEmail()).isEqualTo("novo@a.com");
        assertThat(atualizado.getSenhaHash()).isEqualTo("novo-hash");
        assertThat(atualizado.getRoles()).containsExactly(novaRole);
    }

    @Test
    void atualizar_pessoaDiferenteDaOriginal_lancaExcecao() {
        UUID id = UUID.randomUUID();
        Pessoa pessoaOriginal = pessoaAprovada();
        Usuario existente = Usuario.builder().id(id).pessoa(pessoaOriginal).build();
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(existente));

        UUID outraPessoaId = UUID.randomUUID();
        assertThatThrownBy(() -> usuarioService.atualizar(id, request(outraPessoaId, "a@a.com", Set.of(UUID.randomUUID()))))
                .isInstanceOf(UsuarioPessoaImutavelException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void atualizar_emailDuplicado_lancaExcecao() {
        UUID id = UUID.randomUUID();
        Pessoa pessoa = pessoaAprovada();
        Usuario existente = Usuario.builder().id(id).pessoa(pessoa).build();
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(existente));
        when(usuarioRepository.existsByEmailAndIdNot("ocupado@a.com", id)).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.atualizar(id, request(pessoa.getId(), "ocupado@a.com", Set.of(UUID.randomUUID()))))
                .isInstanceOf(EmailDuplicadoException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void atualizar_lancaExcecaoQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.atualizar(id, request(UUID.randomUUID(), "a@a.com", Set.of(UUID.randomUUID()))))
                .isInstanceOf(UsuarioNaoEncontradoException.class);
        verify(usuarioRepository, never()).save(any());
    }

    // ---------- inativar / reativar ----------

    @Test
    void inativar_marcaComoInativo() {
        UUID id = UUID.randomUUID();
        Usuario existente = Usuario.builder().id(id).ativo(true).build();
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(existente));

        usuarioService.inativar(id);

        assertThat(existente.isAtivo()).isFalse();
        verify(usuarioRepository).save(existente);
    }

    @Test
    void inativar_lancaExcecaoQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.inativar(id)).isInstanceOf(UsuarioNaoEncontradoException.class);
    }

    @Test
    void reativar_marcaComoAtivo() {
        UUID id = UUID.randomUUID();
        Usuario existente = Usuario.builder().id(id).ativo(false).build();
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(existente));
        repositorioDevolveOQueRecebe();

        Usuario reativado = usuarioService.reativar(id);

        assertThat(reativado.isAtivo()).isTrue();
    }

    @Test
    void criar_papelDeCoristaJuntoComOutroPapel_lancaExcecao() {
        Pessoa pessoa = pessoaAprovada();
        Role corista = role("CORISTA_JOVENS", true);
        Role lider = role("LIDER_JOVENS", true);
        when(pessoaService.buscarPorId(pessoa.getId())).thenReturn(pessoa);
        when(usuarioRepository.existsByPessoaId(pessoa.getId())).thenReturn(false);
        when(usuarioRepository.existsByEmail("a@a.com")).thenReturn(false);
        Set<UUID> ids = Set.of(corista.getId(), lider.getId());
        when(roleRepository.findAllById(ids)).thenReturn(List.of(corista, lider));

        assertThatThrownBy(() -> usuarioService.criar(request(pessoa.getId(), "a@a.com", ids)))
                .isInstanceOf(PapelCoristaExclusivoException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void criar_comPapelDeLideranca_avisaQueAPessoaVirouLider() {
        Pessoa pessoa = pessoaAprovada();
        Role lider = role("LIDER_JOVENS", true);
        when(pessoaService.buscarPorId(pessoa.getId())).thenReturn(pessoa);
        when(usuarioRepository.existsByPessoaId(pessoa.getId())).thenReturn(false);
        when(usuarioRepository.existsByEmail("a@a.com")).thenReturn(false);
        when(roleRepository.findAllById(Set.of(lider.getId()))).thenReturn(List.of(lider));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        usuarioService.criar(request(pessoa.getId(), "a@a.com", Set.of(lider.getId())));

        verify(eventos).publishEvent(new PessoaVirouLiderEvent(pessoa.getId()));
    }

    @Test
    void criar_soComPapelDeCorista_naoAvisaNada() {
        Pessoa pessoa = pessoaAprovada();
        Role corista = role("CORISTA_JOVENS", true);
        when(pessoaService.buscarPorId(pessoa.getId())).thenReturn(pessoa);
        when(usuarioRepository.existsByPessoaId(pessoa.getId())).thenReturn(false);
        when(usuarioRepository.existsByEmail("a@a.com")).thenReturn(false);
        when(roleRepository.findAllById(Set.of(corista.getId()))).thenReturn(List.of(corista));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        usuarioService.criar(request(pessoa.getId(), "a@a.com", Set.of(corista.getId())));

        verifyNoInteractions(eventos);
    }

    // ---------- concessão de papéis globais e escopo ----------

    private Usuario autorComPapeis(String... nomes) {
        Usuario autor = new Usuario();
        autor.setId(UUID.randomUUID());
        for (String nome : nomes) {
            autor.getRoles().add(role(nome, true));
        }
        when(usuarioLogado.atual()).thenReturn(Optional.of(autor));
        return autor;
    }

    private void criarUsuarioComRole(Role role) {
        Pessoa pessoa = pessoaAprovada();
        when(pessoaService.buscarPorId(pessoa.getId())).thenReturn(pessoa);
        when(roleRepository.findAllById(Set.of(role.getId()))).thenReturn(List.of(role));
        usuarioService.criar(request(pessoa.getId(), "novo@exemplo.com", Set.of(role.getId())));
    }

    @Test
    void criar_adminPodeConcederPapelGlobal() {
        autorComPapeis("ADMIN");
        Role pastorAdmin = role("PASTOR_ADMIN", true);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        repositorioDevolveOQueRecebe();

        criarUsuarioComRole(pastorAdmin);

        verify(escopoService).ajustarAoPapel(any(Usuario.class));
    }

    @Test
    void criar_quemNaoEAdminNaoConcedeAdminNemPastorAdmin() {
        autorComPapeis("PASTOR");

        assertThatThrownBy(() -> criarUsuarioComRole(role("ADMIN", true)))
                .isInstanceOf(OperacaoReservadaAoAdminException.class);
        assertThatThrownBy(() -> criarUsuarioComRole(role("PASTOR_ADMIN", true)))
                .isInstanceOf(OperacaoReservadaAoAdminException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void criar_pastorAdminSoConcedeOPapelPastor() {
        autorComPapeis("PASTOR_ADMIN");
        when(passwordEncoder.encode(any())).thenReturn("hash");
        repositorioDevolveOQueRecebe();

        criarUsuarioComRole(role("PASTOR", true));

        assertThatThrownBy(() -> criarUsuarioComRole(role("LIDER_MOCIDADE", true)))
                .isInstanceOf(OperacaoReservadaAoAdminException.class);
    }

    @Test
    void criar_semUsuarioLogadoNaoConcedePapelGlobal() {
        when(usuarioLogado.atual()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> criarUsuarioComRole(role("ADMIN", true)))
                .isInstanceOf(OperacaoReservadaAoAdminException.class);
    }

    @Test
    void atualizarInativarEReativar_usuarioGlobalSoPeloAdmin() {
        autorComPapeis("PASTOR_ADMIN");
        Usuario alvo = new Usuario();
        alvo.setId(UUID.randomUUID());
        alvo.setPessoa(pessoaAprovada());
        alvo.getRoles().add(role("ADMIN", true));
        when(usuarioRepository.findById(alvo.getId())).thenReturn(Optional.of(alvo));

        assertThatThrownBy(() -> usuarioService.atualizar(alvo.getId(),
                request(alvo.getPessoa().getId(), "x@exemplo.com", Set.of(UUID.randomUUID()))))
                .isInstanceOf(OperacaoReservadaAoAdminException.class);
        assertThatThrownBy(() -> usuarioService.inativar(alvo.getId()))
                .isInstanceOf(OperacaoReservadaAoAdminException.class);
        assertThatThrownBy(() -> usuarioService.reativar(alvo.getId()))
                .isInstanceOf(OperacaoReservadaAoAdminException.class);
        assertThat(alvo.isAtivo()).isTrue();
    }

    @Test
    void inativar_adminPodeInativarUsuarioGlobal() {
        autorComPapeis("ADMIN");
        Usuario alvo = new Usuario();
        alvo.setId(UUID.randomUUID());
        alvo.setPessoa(pessoaAprovada());
        alvo.getRoles().add(role("PASTOR_ADMIN", true));
        when(usuarioRepository.findById(alvo.getId())).thenReturn(Optional.of(alvo));

        usuarioService.inativar(alvo.getId());

        assertThat(alvo.isAtivo()).isFalse();
    }
}
