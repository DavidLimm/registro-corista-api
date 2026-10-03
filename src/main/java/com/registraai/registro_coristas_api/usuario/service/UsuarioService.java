package com.registraai.registro_coristas_api.usuario.service;

import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Credenciais de acesso (login) de uma {@link Pessoa}. Autenticação/JWT ainda não existem (ver AGENTS.md) — este
 * service só cuida do cadastro do usuário e do hash da senha. Não exige a pessoa {@code APROVADO}: o auto-cadastro
 * (ex.: {@code CoristaService.criar}) cria pessoa e usuário juntos, ainda {@code PENDENTE} — o "acesso" de verdade
 * (login funcionar) fica condicionado à aprovação quando a autenticação existir.
 */
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PessoaService pessoaService;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    /** A pessoa ainda não pode ter usuário; o e-mail é único no sistema. */
    @Transactional
    public Usuario criar(UsuarioRequest request) {
        Pessoa pessoa = pessoaService.buscarPorId(request.pessoaId());
        return criarInterno(pessoa, request.email(), request.senha(), () -> buscarRolesAtivas(request.roleIds()));
    }

    /**
     * Usada pelo auto-cadastro (hoje só {@code CoristaService.criar}): o papel não vem do cliente, é derivado pelo
     * chamador (ex.: pela lista de classificação do corista) — evita que o próprio cadastro se auto-atribua um
     * papel de maior privilégio.
     */
    @Transactional
    public Usuario criarComPapelUnico(Pessoa pessoa, String email, String senha, String nomeDoRole) {
        return criarInterno(pessoa, email, senha, () -> {
            Role role = roleRepository.findByNome(nomeDoRole)
                    .orElseThrow(() -> new IllegalStateException("Role " + nomeDoRole + " não seedada"));
            if (!role.isAtivo()) {
                throw new RoleInativaException(role.getNome());
            }
            return Set.of(role);
        });
    }

    // rolesSupplier só é avaliado depois das checagens de pessoa/e-mail, pra manter a ordem de validação (barato
    // primeiro) e não gastar uma consulta de role à toa quando a criação já ia falhar por outro motivo
    private Usuario criarInterno(Pessoa pessoa, String email, String senha, Supplier<Set<Role>> rolesSupplier) {
        if (usuarioRepository.existsByPessoaId(pessoa.getId())) {
            throw new PessoaJaPossuiUsuarioException(pessoa.getId());
        }
        String emailNormalizado = normalizarEmail(email);
        if (usuarioRepository.existsByEmail(emailNormalizado)) {
            throw new EmailDuplicadoException(emailNormalizado);
        }
        Usuario usuario = new Usuario();
        usuario.setPessoa(pessoa);
        usuario.setEmail(emailNormalizado);
        usuario.setSenhaHash(passwordEncoder.encode(senha));
        usuario.setRoles(rolesSupplier.get());
        return usuarioRepository.save(usuario);
    }

    @Transactional(readOnly = true)
    public Usuario buscarPorId(UUID id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new UsuarioNaoEncontradoException(id));
    }

    /**
     * Paginada e ordenada por e-mail (com desempate por id, para a paginação ser estável).
     * @param ativo {@code null} lista todos; {@code true}/{@code false} filtra por situação.
     */
    @Transactional(readOnly = true)
    public Page<Usuario> listar(Boolean ativo, int pagina, int tamanho) {
        Specification<Usuario> especificacao = Specification.unrestricted();
        if (ativo != null) {
            especificacao = especificacao.and((root, query, cb) -> cb.equal(root.get("ativo"), ativo));
        }
        PageRequest pageable = PageRequest.of(pagina, tamanho, Sort.by("email").and(Sort.by("id")));
        return usuarioRepository.findAll(especificacao, pageable);
    }

    /** PUT substitui tudo, inclusive a senha. A pessoa vinculada é imutável após a criação. */
    @Transactional
    public Usuario atualizar(UUID id, UsuarioRequest request) {
        Usuario usuario = buscarPorId(id);
        if (!usuario.getPessoa().getId().equals(request.pessoaId())) {
            throw new UsuarioPessoaImutavelException();
        }
        String email = normalizarEmail(request.email());
        if (usuarioRepository.existsByEmailAndIdNot(email, id)) {
            throw new EmailDuplicadoException(email);
        }
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(request.senha()));
        usuario.setRoles(buscarRolesAtivas(request.roleIds()));
        return usuarioRepository.save(usuario);
    }

    /** Soft delete: o usuário fica {@code ativo = false} (não deve conseguir autenticar), mas permanece no banco. */
    @Transactional
    public void inativar(UUID id) {
        Usuario usuario = buscarPorId(id);
        usuario.setAtivo(false);
        usuarioRepository.save(usuario);
    }

    @Transactional
    public Usuario reativar(UUID id) {
        Usuario usuario = buscarPorId(id);
        usuario.setAtivo(true);
        return usuarioRepository.save(usuario);
    }

    // busca tudo de uma vez (evita 1 SELECT por role) e só então valida existência/situação
    private Set<Role> buscarRolesAtivas(Set<UUID> roleIds) {
        List<Role> roles = roleRepository.findAllById(roleIds);
        if (roles.size() < roleIds.size()) {
            Set<UUID> encontrados = roles.stream().map(Role::getId).collect(Collectors.toSet());
            UUID faltante = roleIds.stream().filter(id -> !encontrados.contains(id)).findFirst().orElseThrow();
            throw new RoleNaoEncontradaException(faltante);
        }
        for (Role role : roles) {
            if (!role.isAtivo()) {
                throw new RoleInativaException(role.getNome());
            }
        }
        return new LinkedHashSet<>(roles);
    }

    private String normalizarEmail(String email) {
        return email.trim().toLowerCase();
    }
}
