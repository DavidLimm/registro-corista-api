package com.registraai.registro_coristas_api.area.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "area")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Area {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Número oficial da área na IEAD-PE (a matriz é a Área 1). Único. */
    @Column(nullable = false, unique = true)
    private Integer numero;

    @Column(nullable = false, length = 100)
    private String nome;

    /** Soft delete: área inativa continua no banco, mas não deve ser oferecida para novos vínculos. */
    @Builder.Default
    @Column(nullable = false)
    private boolean ativa = true;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;
}
