package com.registraai.registro_coristas_api.pessoa.repository;

import com.registraai.registro_coristas_api.pessoa.model.HistoricoStatusPessoa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HistoricoStatusPessoaRepository extends JpaRepository<HistoricoStatusPessoa, UUID> {
}
