package com.registraai.registro_coristas_api.agrupamento.repository;

import com.registraai.registro_coristas_api.agrupamento.model.Subdivisao;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubdivisaoRepository extends JpaRepository<Subdivisao, UUID> {

    // agrupamento é LAZY e o open-in-view está desligado: carrega junto para o Controller montar o Response
    @EntityGraph(attributePaths = "agrupamento")
    Optional<Subdivisao> findByIdAndAgrupamentoId(UUID id, UUID agrupamentoId);

    @EntityGraph(attributePaths = "agrupamento")
    List<Subdivisao> findAllByAgrupamentoIdOrderByNumeroAscNomeAsc(UUID agrupamentoId);

    @EntityGraph(attributePaths = "agrupamento")
    List<Subdivisao> findAllByAgrupamentoIdAndAtivoOrderByNumeroAscNomeAsc(UUID agrupamentoId, boolean ativo);

    boolean existsByAgrupamentoIdAndNumero(UUID agrupamentoId, Integer numero);

    boolean existsByAgrupamentoIdAndNumeroAndIdNot(UUID agrupamentoId, Integer numero, UUID id);

    boolean existsByAgrupamentoIdAndNomeIgnoreCase(UUID agrupamentoId, String nome);

    boolean existsByAgrupamentoIdAndNomeIgnoreCaseAndIdNot(UUID agrupamentoId, String nome, UUID id);
}
