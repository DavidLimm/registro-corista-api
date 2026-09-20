package com.registraai.registro_coristas_api.pessoa.repository;

import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface PessoaRepository extends JpaRepository<Pessoa, UUID>, JpaSpecificationExecutor<Pessoa> {

    // congregacao, area e endereco são LAZY e o open-in-view está desligado: carregar junto para o Controller poder
    // montar o Response (só relações to-one, então a paginação continua sendo feita no banco)
    @Override
    @EntityGraph(attributePaths = {"congregacao", "congregacao.area", "endereco"})
    Optional<Pessoa> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = {"congregacao", "congregacao.area", "endereco"})
    Page<Pessoa> findAll(Specification<Pessoa> spec, Pageable pageable);
}
