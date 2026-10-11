package com.registraai.registro_coristas_api.afastamento.repository;

import com.registraai.registro_coristas_api.afastamento.model.SolicitacaoAfastamento;
import com.registraai.registro_coristas_api.afastamento.model.StatusSolicitacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;
import java.util.UUID;

public interface SolicitacaoAfastamentoRepository
        extends JpaRepository<SolicitacaoAfastamento, UUID>, JpaSpecificationExecutor<SolicitacaoAfastamento> {

    // o SolicitacaoAfastamentoResponse mostra o nome da pessoa e o open-in-view está desligado
    @Override
    @EntityGraph(attributePaths = {"pessoa"})
    Optional<SolicitacaoAfastamento> findById(UUID id);

    @EntityGraph(attributePaths = {"pessoa"})
    Page<SolicitacaoAfastamento> findByStatus(StatusSolicitacao status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"pessoa"})
    Page<SolicitacaoAfastamento> findAll(Pageable pageable);

    // a fila filtrada pelo escopo do usuário (o response também mostra o nome da pessoa)
    @Override
    @EntityGraph(attributePaths = {"pessoa"})
    Page<SolicitacaoAfastamento> findAll(Specification<SolicitacaoAfastamento> spec, Pageable pageable);

    boolean existsByPessoaIdAndStatus(UUID pessoaId, StatusSolicitacao status);
}
