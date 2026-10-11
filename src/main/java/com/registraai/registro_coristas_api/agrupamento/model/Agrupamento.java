package com.registraai.registro_coristas_api.agrupamento.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "agrupamento")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Agrupamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoAgrupamento tipo;

    /** Obrigatório em campo missionário, filial e setor; ausente em área. */
    @Column(length = 150)
    private String nome;

    /** Obrigatório em setor e área; ausente nos demais. Único só dentro do tipo (setor/área). */
    private Integer numero;

    /** Obrigatório apenas em campo missionário. */
    @Column(length = 100)
    private String pais;

    /** Soft delete: inativo continua no banco, mas não deve ser oferecido para novos vínculos. */
    @Builder.Default
    @Column(nullable = false)
    private boolean ativo = true;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;
}
