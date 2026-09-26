package com.registraai.registro_coristas_api.role.repository;

import com.registraai.registro_coristas_api.role.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    boolean existsByNome(String nome);

    boolean existsByNomeAndIdNot(String nome, UUID id);

    List<Role> findAllByOrderByNomeAsc();

    List<Role> findAllByAtivoOrderByNomeAsc(boolean ativo);
}
