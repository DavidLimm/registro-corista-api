package com.registraai.registro_coristas_api.afastamento.model;

import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * Pedido de afastamento de uma pessoa feito por quem não tem poder para afastar. O afastamento em si
 * ({@code APROVADO -> AFASTADO}) só acontece quando alguém com {@code CORISTA_APROVAR_AFASTAMENTO} aprova o pedido.
 */
@Entity
@Table(name = "solicitacao_afastamento")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolicitacaoAfastamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pessoa_id", nullable = false)
    private Pessoa pessoa;

    @Column(nullable = false, length = 500)
    private String motivo;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusSolicitacao status = StatusSolicitacao.PENDENTE;

    @Column(name = "solicitado_por", nullable = false)
    private UUID solicitadoPor;

    @CreationTimestamp
    @Column(name = "solicitado_em", nullable = false, updatable = false)
    private Instant solicitadoEm;

    @Column(name = "decidido_por")
    private UUID decididoPor;

    @Column(name = "decidido_em")
    private Instant decididoEm;
}
