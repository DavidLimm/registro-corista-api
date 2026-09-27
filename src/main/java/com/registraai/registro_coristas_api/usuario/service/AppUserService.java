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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Credenciais de acesso (login) de uma {@link Pessoa} já aprovada. Autenticação/JWT ainda não existem (ver
 * AGENTS.md) — este service só cuida do cadastro do usuário e do hash da senha.
 */
@Service
@RequiredArgsConstructor
public class AppUserService {

    private final AppUserRepository appUserRepository;
    private final PessoaService pessoaService;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    /** A pessoa precisa estar APROVADA e ainda não ter usuário; o e-mail é único no sistema. */
    @Transactional
    public AppUser criar(AppUserRequest request) {
        Pessoa pessoa = buscarPessoaAprovada(request.pessoaId());
        if (appUserRepository.existsByPessoaId(pessoa.getId())) {
            throw new PessoaJaPossuiUsuarioException(pessoa.getId());
        }
        String email = normalizarEmail(request.email());
        if (appUserRepository.existsByEmail(email)) {
            throw new EmailDuplicadoException(email);
        }
        AppUser appUser = new AppUser();
        appUser.setPessoa(pessoa);
        appUser.setEmail(email);
        appUser.setSenhaHash(passwordEncoder.encode(request.senha()));
        appUser.setRoles(buscarRolesAtivas(request.roleIds()));
        return appUserRepository.save(appUser);
    }

    @Transactional(readOnly = true)
    public AppUser buscarPorId(UUID id) {
        return appUserRepository.findById(id)
                .orElseThrow(() -> new AppUserNaoEncontradoException(id));
    }

    /** @param ativo {@code null} lista todos; {@code true}/{@code false} filtra por situação. */
    @Transactional(readOnly = true)
    public List<AppUser> listar(Boolean ativo) {
        if (ativo == null) {
            return appUserRepository.findAllByOrderByEmailAsc();
        }
        return appUserRepository.findAllByAtivoOrderByEmailAsc(ativo);
    }

    /** PUT substitui tudo, inclusive a senha. A pessoa vinculada é imutável após a criação. */
    @Transactional
    public AppUser atualizar(UUID id, AppUserRequest request) {
        AppUser appUser = buscarPorId(id);
        if (!appUser.getPessoa().getId().equals(request.pessoaId())) {
            throw new AppUserPessoaImutavelException();
        }
        String email = normalizarEmail(request.email());
        if (appUserRepository.existsByEmailAndIdNot(email, id)) {
            throw new EmailDuplicadoException(email);
        }
        appUser.setEmail(email);
        appUser.setSenhaHash(passwordEncoder.encode(request.senha()));
        appUser.setRoles(buscarRolesAtivas(request.roleIds()));
        return appUserRepository.save(appUser);
    }

    /** Soft delete: o usuário fica {@code ativo = false} (não deve conseguir autenticar), mas permanece no banco. */
    @Transactional
    public void inativar(UUID id) {
        AppUser appUser = buscarPorId(id);
        appUser.setAtivo(false);
        appUserRepository.save(appUser);
    }

    @Transactional
    public AppUser reativar(UUID id) {
        AppUser appUser = buscarPorId(id);
        appUser.setAtivo(true);
        return appUserRepository.save(appUser);
    }

    private Pessoa buscarPessoaAprovada(UUID pessoaId) {
        Pessoa pessoa = pessoaService.buscarPorId(pessoaId);
        if (pessoa.getStatus() != StatusPessoa.APROVADO) {
            throw new PessoaNaoAprovadaException(pessoa.getId());
        }
        return pessoa;
    }

    private Set<Role> buscarRolesAtivas(Set<UUID> roleIds) {
        Set<Role> roles = new LinkedHashSet<>();
        for (UUID roleId : roleIds) {
            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new RoleNaoEncontradaException(roleId));
            if (!role.isAtivo()) {
                throw new RoleInativaException(role.getNome());
            }
            roles.add(role);
        }
        return roles;
    }

    private String normalizarEmail(String email) {
        return email.trim().toLowerCase();
    }
}
