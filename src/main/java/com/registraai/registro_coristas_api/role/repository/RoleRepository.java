package com.registraai.registro_coristas_api.role.repository;

import com.registraai.registro_coristas_api.role.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    @Query("select p.nome from Role r join r.permissoes p where r.nome = :nomeDaRole")
    List<String> findNomesDePermissoesPorNomeDaRole(@Param("nomeDaRole") String nomeDaRole);

    Optional<Role> findByNome(String nome);

    boolean existsByNome(String nome);

    boolean existsByNomeAndIdNot(String nome, UUID id);

    List<Role> findAllByOrderByNomeAsc();

    List<Role> findAllByAtivoOrderByNomeAsc(boolean ativo);
}
