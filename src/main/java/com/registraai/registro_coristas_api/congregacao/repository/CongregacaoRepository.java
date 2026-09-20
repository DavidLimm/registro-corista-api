package com.registraai.registro_coristas_api.congregacao.repository;

import com.registraai.registro_coristas_api.congregacao.model.Congregacao;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CongregacaoRepository extends JpaRepository<Congregacao, UUID>, JpaSpecificationExecutor<Congregacao> {

    long countByAreaIdAndAtivaTrue(UUID areaId);

    boolean existsByAreaIdAndNomeIgnoreCase(UUID areaId, String nome);

    boolean existsByAreaIdAndNomeIgnoreCaseAndIdNot(UUID areaId, String nome, UUID id);

    // area e endereco são LAZY e o open-in-view está desligado: carregar junto para o Controller poder montar o Response
    @Override
    @EntityGraph(attributePaths = {"area", "endereco"})
    Optional<Congregacao> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = {"area", "endereco"})
    List<Congregacao> findAll(Specification<Congregacao> spec, Sort sort);
}
