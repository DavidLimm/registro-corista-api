package com.registraai.registro_coristas_api.corista.repository;

import com.registraai.registro_coristas_api.corista.model.Corista;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface CoristaRepository extends JpaRepository<Corista, UUID>, JpaSpecificationExecutor<Corista> {

    // tudo que o CoristaResponse acessa é LAZY e o open-in-view está desligado: carregar junto (só relações to-one,
    // então a paginação continua sendo feita no banco)
    @Override
    @EntityGraph(attributePaths = {"pessoa", "pessoa.congregacao", "pessoa.congregacao.area", "pessoa.endereco"})
    Optional<Corista> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = {"pessoa", "pessoa.congregacao", "pessoa.congregacao.area", "pessoa.endereco"})
    Page<Corista> findAll(Specification<Corista> spec, Pageable pageable);
}
