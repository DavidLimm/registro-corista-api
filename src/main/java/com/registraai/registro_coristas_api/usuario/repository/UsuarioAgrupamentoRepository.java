package com.registraai.registro_coristas_api.usuario.repository;

import com.registraai.registro_coristas_api.usuario.model.UsuarioAgrupamento;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface UsuarioAgrupamentoRepository extends JpaRepository<UsuarioAgrupamento, UUID> {

    // agrupamento é LAZY e o open-in-view está desligado: carrega junto para o Controller montar o Response
    @EntityGraph(attributePaths = "agrupamento")
    List<UsuarioAgrupamento> findAllByUsuarioId(UUID usuarioId);

    @Modifying
    @Query("DELETE FROM UsuarioAgrupamento v WHERE v.usuario.id = :usuarioId")
    void removerTodosDoUsuario(@Param("usuarioId") UUID usuarioId);

    @Modifying
    @Query("DELETE FROM UsuarioAgrupamento v WHERE v.usuario.id = :usuarioId AND v.agrupamento.id IN :agrupamentoIds")
    void removerDoUsuario(@Param("usuarioId") UUID usuarioId, @Param("agrupamentoIds") Collection<UUID> agrupamentoIds);
}
