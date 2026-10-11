package com.registraai.registro_coristas_api.role.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Linha da tabela {@code permissao}: o catálogo de permissões gravado no banco. O nome precisa existir no enum
 * {@link Permissao}, que é quem o código de fato verifica. Hoje só é lido; alterar o vínculo role ↔ permissão
 * acontece em {@link Role#getPermissoes()}.
 */
@Entity
@Table(name = "permissao")
@Getter
@Setter
@NoArgsConstructor
public class PermissaoRegistro {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 60, unique = true)
    private String nome;

    @Column(nullable = false, length = 255)
    private String descricao;
}
