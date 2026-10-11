package com.registraai.registro_coristas_api.congregacao.repository;

import com.registraai.registro_coristas_api.congregacao.model.Congregacao;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CongregacaoRepository extends JpaRepository<Congregacao, UUID>, JpaSpecificationExecutor<Congregacao> {

    @Query("SELECT count(c) FROM Congregacao c WHERE c.subdivisao.agrupamento.id = :agrupamentoId AND c.ativa = true")
    long contarAtivasPorAgrupamento(@Param("agrupamentoId") UUID agrupamentoId);

    long countBySubdivisaoIdAndAtivaTrue(UUID subdivisaoId);

    boolean existsBySubdivisaoIdAndNomeIgnoreCase(UUID subdivisaoId, String nome);

    boolean existsBySubdivisaoIdAndNomeIgnoreCaseAndIdNot(UUID subdivisaoId, String nome, UUID id);

    // subdivisao, agrupamento e endereco são LAZY e o open-in-view está desligado: carregar junto para o Controller
    // poder montar o Response
    @Override
    @EntityGraph(attributePaths = {"subdivisao", "subdivisao.agrupamento", "endereco"})
    Optional<Congregacao> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = {"subdivisao", "subdivisao.agrupamento", "endereco"})
    List<Congregacao> findAll(Specification<Congregacao> spec, Sort sort);
}
