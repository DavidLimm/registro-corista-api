package com.registraai.registro_coristas_api.telefone.model;

import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
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

/**
 * Telefone de contato de uma {@link Pessoa}. Uma pessoa pode ter vários; no máximo um é o principal (garantido
 * no banco por índice único parcial) e o mesmo número não se repete para a mesma pessoa.
 */
@Entity
@Table(name = "telefone")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Telefone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pessoa_id", nullable = false)
    private Pessoa pessoa;

    /** Somente dígitos, com DDD (10 ou 11). */
    @Column(nullable = false, length = 20)
    private String numero;

    @Column(nullable = false)
    private boolean whatsapp;

    @Column(nullable = false)
    private boolean principal;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;
}
