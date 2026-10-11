package com.registraai.registro_coristas_api.role.service;

import com.registraai.registro_coristas_api.role.dto.RoleRequest;
import com.registraai.registro_coristas_api.role.exception.PermissaoInexistenteException;
import com.registraai.registro_coristas_api.role.exception.RoleNaoEncontradaException;
import com.registraai.registro_coristas_api.role.exception.RoleNaoPendenteException;
import com.registraai.registro_coristas_api.role.exception.RoleNomeDuplicadoException;
import com.registraai.registro_coristas_api.role.exception.RoleProtegidaException;
import com.registraai.registro_coristas_api.role.model.PermissaoRegistro;
import com.registraai.registro_coristas_api.role.model.Role;
import com.registraai.registro_coristas_api.role.model.StatusRole;
import com.registraai.registro_coristas_api.role.repository.PermissaoRegistroRepository;
import com.registraai.registro_coristas_api.role.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    /** O papel que sempre tem todas as permissões: nunca editado pela API (novas permissões entram por migration). */
    private static final String ROLE_ADMIN = "ADMIN";

    private final RoleRepository roleRepository;
    private final PermissaoRegistroRepository permissaoRegistroRepository;
    private final Clock clock;

    /** Criação direta (por quem gerencia papéis): já nasce aprovada e ativa. */
    @Transactional
    public Role criar(RoleRequest request) {
        String nome = request.nome().trim().toUpperCase();
        if (roleRepository.existsByNome(nome)) {
            throw new RoleNomeDuplicadoException(nome);
        }
        Role role = new Role();
        preencher(role, request);
        return roleRepository.save(role);
    }

    /**
     * Pedido de um papel novo: nasce {@code PENDENTE} e inativo (não pode ser atribuído a ninguém) até o PASTOR decidir.
     * O nome do pedido também não pode repetir um papel que já existe.
     */
    @Transactional
    public Role solicitar(RoleRequest request, UUID solicitadoPor) {
        String nome = request.nome().trim().toUpperCase();
        if (roleRepository.existsByNome(nome)) {
            throw new RoleNomeDuplicadoException(nome);
        }
        Role role = new Role();
        preencher(role, request);
        role.setAtivo(false);
        role.setStatus(StatusRole.PENDENTE);
        role.setSolicitadoPor(solicitadoPor);
        return roleRepository.save(role);
    }

    /** {@code PENDENTE -> APROVADA}: o papel passa a poder ser atribuído (as permissões são dadas à parte). */
    @Transactional
    public Role aprovar(UUID id, UUID aprovadoPor) {
        Role role = pendente(id);
        role.setStatus(StatusRole.APROVADA);
        role.setAtivo(true);
        registrarDecisao(role, aprovadoPor);
        return roleRepository.save(role);
    }

    /** {@code PENDENTE -> REPROVADA}: o papel continua inativo e não volta por {@link #reativar}. */
    @Transactional
    public Role reprovar(UUID id, UUID reprovadoPor) {
        Role role = pendente(id);
        role.setStatus(StatusRole.REPROVADA);
        registrarDecisao(role, reprovadoPor);
        return roleRepository.save(role);
    }

    @Transactional(readOnly = true)
    public Role buscarPorId(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new RoleNaoEncontradaException(id));
    }

    /**
     * @param ativo  {@code null} lista todos; {@code true}/{@code false} filtra por situação.
     * @param status {@code null} não filtra; senão só os papéis nessa situação do fluxo (ex.: os pendentes).
     */
    @Transactional(readOnly = true)
    public List<Role> listar(Boolean ativo, StatusRole status) {
        List<Role> roles = ativo == null
                ? roleRepository.findAllByOrderByNomeAsc()
                : roleRepository.findAllByAtivoOrderByNomeAsc(ativo);
        if (status == null) {
            return roles;
        }
        return roles.stream().filter(role -> role.getStatus() == status).toList();
    }

    @Transactional
    public Role atualizar(UUID id, RoleRequest request) {
        Role role = buscarPorId(id);
        String nome = request.nome().trim().toUpperCase();
        if (roleRepository.existsByNomeAndIdNot(nome, id)) {
            throw new RoleNomeDuplicadoException(nome);
        }
        preencher(role, request);
        return roleRepository.save(role);
    }

    /** Soft delete: o papel permanece no banco com {@code ativo = false}. */
    @Transactional
    public void inativar(UUID id) {
        Role role = buscarPorId(id);
        role.setAtivo(false);
        roleRepository.save(role);
    }

    /** Só reativa papel que já foi aprovado: pendente ou reprovado passa pela decisão do PASTOR. */
    @Transactional
    public Role reativar(UUID id) {
        Role role = buscarPorId(id);
        if (role.getStatus() != StatusRole.APROVADA) {
            throw new RoleNaoPendenteException(role.getNome());
        }
        role.setAtivo(true);
        return roleRepository.save(role);
    }

    /** Nomes das permissões do papel, em ordem alfabética. */
    @Transactional(readOnly = true)
    public Set<String> permissoesDe(UUID id) {
        return nomes(buscarPorId(id).getPermissoes());
    }

    /**
     * Substitui as permissões do papel pelo conjunto informado (vazio tira todas). Vale na hora para quem tem o papel,
     * porque as permissões são lidas do banco a cada requisição. O {@code ADMIN} não é editável.
     */
    @Transactional
    public Set<String> definirPermissoes(UUID id, Set<String> nomesPedidos) {
        Role role = buscarPorId(id);
        if (ROLE_ADMIN.equals(role.getNome())) {
            throw new RoleProtegidaException(role.getNome());
        }
        Set<String> pedidos = nomesPedidos.stream().map(String::trim).collect(Collectors.toSet());
        List<PermissaoRegistro> encontradas = permissaoRegistroRepository.findByNomeIn(pedidos);
        Set<String> desconhecidas = new TreeSet<>(pedidos);
        encontradas.forEach(permissao -> desconhecidas.remove(permissao.getNome()));
        if (!desconhecidas.isEmpty()) {
            throw new PermissaoInexistenteException(desconhecidas);
        }
        role.setPermissoes(new HashSet<>(encontradas));
        roleRepository.save(role);
        return nomes(role.getPermissoes());
    }

    private Role pendente(UUID id) {
        Role role = buscarPorId(id);
        if (role.getStatus() != StatusRole.PENDENTE) {
            throw new RoleNaoPendenteException(role.getNome());
        }
        return role;
    }

    private void registrarDecisao(Role role, UUID decididoPor) {
        role.setDecididoPor(decididoPor);
        role.setDecididoEm(Instant.now(clock));
    }

    private Set<String> nomes(Set<PermissaoRegistro> permissoes) {
        return permissoes.stream().map(PermissaoRegistro::getNome).collect(Collectors.toCollection(TreeSet::new));
    }

    private void preencher(Role role, RoleRequest request) {
        role.setNome(request.nome().trim().toUpperCase());
        role.setDescricao(request.descricao().trim());
    }
}
