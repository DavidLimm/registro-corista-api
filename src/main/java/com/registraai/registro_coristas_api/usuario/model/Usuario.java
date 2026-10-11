package com.registraai.registro_coristas_api.usuario.model;

import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.pessoa.model.StatusPessoa;
import com.registraai.registro_coristas_api.role.model.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Credenciais de acesso de uma {@link Pessoa}. Nem toda pessoa tem um usuário (ex.: menor gerenciado pelo líder),
 * mas todo usuário aponta para exatamente uma pessoa (relação 1:1) — a identidade continua representada uma única
 * vez. Papéis (RBAC) são vínculos em {@link #roles}, nunca duplicação de cadastro.
 */
@Entity
@Table(name = "usuario")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Usuario implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pessoa_id", nullable = false, unique = true)
    private Pessoa pessoa;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 255)
    private String senhaHash;

    /** Soft delete: usuário inativo não deve conseguir autenticar, mas permanece no banco. */
    @Builder.Default
    @Column(nullable = false)
    private boolean ativo = true;

    /** Senhas erradas seguidas desde o último acerto (ver {@code ControleDeLogin}). */
    @Builder.Default
    @Column(name = "tentativas_falhas", nullable = false)
    private int tentativasFalhas = 0;

    /** Preenchido quando a conta é bloqueada por tentativas; só a redefinição de senha limpa. */
    @Column(name = "bloqueado_em")
    private Instant bloqueadoEm;

    /** JWT emitidos antes disto não valem mais (troca ou redefinição de senha). */
    @Column(name = "senha_alterada_em")
    private Instant senhaAlteradaEm;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "usuario_role",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    /**
     * Um {@code ROLE_<nome>} por papel e uma autoridade por permissão concedida a eles (ex.: {@code CORISTA_APROVAR}).
     * As permissões vêm do banco ({@code role_permissao}), então o PASTOR as altera sem deploy; o código checa
     * a permissão ({@code hasAuthority}), não o nome do papel.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> autoridades = new HashSet<>();
        for (Role role : roles) {
            autoridades.add(new SimpleGrantedAuthority("ROLE_" + role.getNome()));
            role.getPermissoes().forEach(permissao -> autoridades.add(new SimpleGrantedAuthority(permissao.getNome())));
        }
        return autoridades;
    }

    @Override
    public String getPassword() {
        return senhaHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonLocked() {
        return bloqueadoEm == null;
    }

    @Override
    public boolean isEnabled() {
        return ativo && pessoa.getStatus() == StatusPessoa.APROVADO;
    }
}
