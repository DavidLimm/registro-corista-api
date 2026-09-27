package com.registraai.registro_coristas_api.usuario.repository;

import com.registraai.registro_coristas_api.usuario.model.AppUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, UUID id);

    boolean existsByPessoaId(UUID pessoaId);

    // tudo que o AppUserResponse acessa é LAZY e o open-in-view está desligado: carregar junto
    @Override
    @EntityGraph(attributePaths = {"pessoa", "roles"})
    Optional<AppUser> findById(UUID id);

    @EntityGraph(attributePaths = {"pessoa", "roles"})
    List<AppUser> findAllByOrderByEmailAsc();

    @EntityGraph(attributePaths = {"pessoa", "roles"})
    List<AppUser> findAllByAtivoOrderByEmailAsc(boolean ativo);
}
