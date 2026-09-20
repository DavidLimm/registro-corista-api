package com.registraai.registro_coristas_api.congregacao.repository;

import com.registraai.registro_coristas_api.congregacao.model.Congregacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CongregacaoRepository extends JpaRepository<Congregacao, UUID> {

    long countByAreaIdAndAtivaTrue(UUID areaId);
}
