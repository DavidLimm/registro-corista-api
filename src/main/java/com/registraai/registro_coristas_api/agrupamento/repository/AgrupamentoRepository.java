package com.registraai.registro_coristas_api.agrupamento.repository;

import com.registraai.registro_coristas_api.agrupamento.model.Agrupamento;
import com.registraai.registro_coristas_api.agrupamento.model.TipoAgrupamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface AgrupamentoRepository extends JpaRepository<Agrupamento, UUID>, JpaSpecificationExecutor<Agrupamento> {

    boolean existsByTipoAndNumero(TipoAgrupamento tipo, Integer numero);

    boolean existsByTipoAndNumeroAndIdNot(TipoAgrupamento tipo, Integer numero, UUID id);

    boolean existsByTipoAndNomeIgnoreCase(TipoAgrupamento tipo, String nome);

    boolean existsByTipoAndNomeIgnoreCaseAndIdNot(TipoAgrupamento tipo, String nome, UUID id);
}
