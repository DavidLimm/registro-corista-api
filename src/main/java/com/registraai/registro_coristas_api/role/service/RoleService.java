package com.registraai.registro_coristas_api.role.service;

import com.registraai.registro_coristas_api.role.dto.RoleRequest;
import com.registraai.registro_coristas_api.role.exception.RoleNaoEncontradaException;
import com.registraai.registro_coristas_api.role.exception.RoleNomeDuplicadoException;
import com.registraai.registro_coristas_api.role.model.Role;
import com.registraai.registro_coristas_api.role.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

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

    @Transactional(readOnly = true)
    public Role buscarPorId(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new RoleNaoEncontradaException(id));
    }

    /** @param ativo {@code null} lista todos; {@code true}/{@code false} filtra por situação. */
    @Transactional(readOnly = true)
    public List<Role> listar(Boolean ativo) {
        if (ativo == null) {
            return roleRepository.findAllByOrderByNomeAsc();
        }
        return roleRepository.findAllByAtivoOrderByNomeAsc(ativo);
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

    @Transactional
    public Role reativar(UUID id) {
        Role role = buscarPorId(id);
        role.setAtivo(true);
        return roleRepository.save(role);
    }

    private void preencher(Role role, RoleRequest request) {
        role.setNome(request.nome().trim().toUpperCase());
        role.setDescricao(request.descricao().trim());
    }
}
