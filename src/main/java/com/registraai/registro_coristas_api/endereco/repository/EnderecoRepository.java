package com.registraai.registro_coristas_api.endereco.repository;

import com.registraai.registro_coristas_api.endereco.model.Endereco;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EnderecoRepository extends JpaRepository<Endereco, UUID> {
}
