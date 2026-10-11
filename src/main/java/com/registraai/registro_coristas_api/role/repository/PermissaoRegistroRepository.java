package com.registraai.registro_coristas_api.role.repository;

import com.registraai.registro_coristas_api.role.model.PermissaoRegistro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PermissaoRegistroRepository extends JpaRepository<PermissaoRegistro, UUID> {

    List<PermissaoRegistro> findByNomeIn(Collection<String> nomes);
}
