package com.registraai.registro_coristas_api.area.repository;

import com.registraai.registro_coristas_api.area.model.Area;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AreaRepository extends JpaRepository<Area, UUID> {

    boolean existsByNumero(Integer numero);

    boolean existsByNumeroAndIdNot(Integer numero, UUID id);

    List<Area> findAllByOrderByNumeroAsc();

    List<Area> findAllByAtivaOrderByNumeroAsc(boolean ativa);
}
