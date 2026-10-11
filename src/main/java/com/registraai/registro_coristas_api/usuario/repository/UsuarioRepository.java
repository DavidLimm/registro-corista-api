package com.registraai.registro_coristas_api.usuario.repository;

import com.registraai.registro_coristas_api.usuario.model.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID>, JpaSpecificationExecutor<Usuario> {

    @EntityGraph(attributePaths = {"pessoa", "pessoa.congregacao", "roles", "roles.permissoes"})
    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndBloqueadoEmIsNotNull(String email);

    // contadores de login em UPDATE atômico: duas tentativas simultâneas não se perdem num "ler, somar, gravar"
    @Modifying
    @Query("update Usuario u set u.tentativasFalhas = u.tentativasFalhas + 1 where u.email = :email and u.bloqueadoEm is null")
    int registrarFalhaDeLogin(@Param("email") String email);

    @Modifying
    @Query("update Usuario u set u.bloqueadoEm = :agora where u.email = :email and u.bloqueadoEm is null "
            + "and u.tentativasFalhas >= :limite")
    int bloquearSeAtingiuOLimite(@Param("email") String email, @Param("limite") int limite, @Param("agora") Instant agora);

    @Modifying
    @Query("update Usuario u set u.tentativasFalhas = 0 where u.email = :email and u.bloqueadoEm is null "
            + "and u.tentativasFalhas > 0")
    int zerarFalhasDeLogin(@Param("email") String email);

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
