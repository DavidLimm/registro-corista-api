package com.registraai.registro_coristas_api.role.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "role")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Nome do papel usado no RBAC (ex.: ADMIN, PASTOR, LIDER_MOCIDADE). Único. */
    @Column(nullable = false, length = 50)
    private String nome;

    @Column(nullable = false, length = 255)
    private String descricao;

    /** Soft delete: papel inativo continua no banco, mas não deve ser oferecido para novos vínculos. */
    @Builder.Default
    @Column(nullable = false)
    private boolean ativo = true;

    /** Fluxo de solicitação: papel pedido nasce PENDENTE e inativo; os criados direto (ou antigos) são APROVADA. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusRole status = StatusRole.APROVADA;

    @Column(name = "solicitado_por")
    private UUID solicitadoPor;

    @Column(name = "decidido_por")
    private UUID decididoPor;

    @Column(name = "decidido_em")
    private Instant decididoEm;

    /** Permissões concedidas a este papel, gravadas em {@code role_permissao}. Editáveis pelo PASTOR. */
    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_permissao",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permissao_id")
    )
    private Set<PermissaoRegistro> permissoes = new HashSet<>();

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;
}
