package com.registraai.registro_coristas_api.telefone.repository;

import com.registraai.registro_coristas_api.telefone.model.Telefone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TelefoneRepository extends JpaRepository<Telefone, UUID> {

    /** O principal vem primeiro; os demais na ordem em que foram cadastrados. */
    List<Telefone> findByPessoaIdOrderByPrincipalDescCriadoEmAsc(UUID pessoaId);

    /** Filtra também pela pessoa: um telefone só é alcançável pela pessoa dona dele. */
    Optional<Telefone> findByIdAndPessoaId(UUID id, UUID pessoaId);

    Optional<Telefone> findByPessoaIdAndPrincipalTrue(UUID pessoaId);

    boolean existsByPessoaId(UUID pessoaId);

    boolean existsByPessoaIdAndNumero(UUID pessoaId, String numero);

    boolean existsByPessoaIdAndNumeroAndIdNot(UUID pessoaId, String numero, UUID id);
}
