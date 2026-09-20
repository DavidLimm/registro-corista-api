package com.registraai.registro_coristas_api.pessoa.repository;

import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PessoaRepository extends JpaRepository<Pessoa, UUID> {
}
