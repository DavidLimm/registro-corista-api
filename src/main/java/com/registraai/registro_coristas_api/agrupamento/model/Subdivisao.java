package com.registraai.registro_coristas_api.agrupamento.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

/** Nível do meio: "Local" (campo missionário), Área (filial/setor) ou a subdivisão implícita do tipo AREA. */
@Entity
@Table(name = "subdivisao")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subdivisao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agrupamento_id", nullable = false, updatable = false)
    private Agrupamento agrupamento;

    /** Obrigatório em filial/setor; ausente em campo missionário e na subdivisão implícita. */
    private Integer numero;

    /** Obrigatório em campo missionário; ausente nos demais. */
    @Column(length = 150)
    private String nome;

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
