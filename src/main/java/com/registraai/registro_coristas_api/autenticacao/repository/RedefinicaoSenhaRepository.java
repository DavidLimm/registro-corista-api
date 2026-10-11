package com.registraai.registro_coristas_api.autenticacao.repository;

import com.registraai.registro_coristas_api.autenticacao.model.RedefinicaoSenha;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RedefinicaoSenhaRepository extends JpaRepository<RedefinicaoSenha, UUID> {

    @EntityGraph(attributePaths = {"usuario"})
    Optional<RedefinicaoSenha> findByTokenHash(String tokenHash);

    boolean existsByUsuarioIdAndCriadoEmAfter(UUID usuarioId, Instant instante);

    /** Pedidos antigos ainda não usados deixam de valer quando sai um novo: só o último link funciona. */
    @Modifying
    @Query("update RedefinicaoSenha r set r.usadoEm = :agora where r.usuario.id = :usuarioId and r.usadoEm is null")
    int invalidarPendentes(@Param("usuarioId") UUID usuarioId, @Param("agora") Instant agora);
}
