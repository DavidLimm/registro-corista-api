package com.registraai.registro_coristas_api.usuario.model;

import com.registraai.registro_coristas_api.agrupamento.model.Agrupamento;
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

import java.time.Instant;
import java.util.UUID;

/** Escopo do usuário: um vínculo com um agrupamento. Quantos vínculos cada papel pode ter é regra do Service. */
@Entity
@Table(name = "usuario_agrupamento")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioAgrupamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agrupamento_id", nullable = false, updatable = false)
    private Agrupamento agrupamento;

    /** Quem fez o vínculo; {@code null} quando ele nasce do auto-cadastro (vem da congregação escolhida). */
    @Column(name = "vinculado_por", updatable = false)
    private UUID vinculadoPor;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;
}
