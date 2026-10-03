package com.registraai.registro_coristas_api.usuario.repository;

import com.registraai.registro_coristas_api.usuario.model.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID>, JpaSpecificationExecutor<Usuario> {

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, UUID id);

    boolean existsByPessoaId(UUID pessoaId);

    // tudo que o UsuarioResponse acessa é LAZY e o open-in-view está desligado: carregar junto (só relações to-one
    // ou coleção pequena de roles, então a paginação continua sendo feita no banco)
    @Override
    @EntityGraph(attributePaths = {"pessoa", "roles"})
    Optional<Usuario> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = {"pessoa", "roles"})
    Page<Usuario> findAll(Specification<Usuario> spec, Pageable pageable);
}
