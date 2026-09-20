package com.registraai.registro_coristas_api.pessoa.model;

import com.registraai.registro_coristas_api.congregacao.model.Congregacao;
import com.registraai.registro_coristas_api.endereco.model.Endereco;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

/**
 * Dados comuns a qualquer pessoa do sistema. Papéis específicos (ex.: {@code Corista}) são especializações
 * 1:1 que apontam para esta entidade; a pessoa é representada uma única vez.
 * A faixa etária real nunca é gravada: é sempre derivada de {@link #dataNascimento}.
 */
@Entity
@Table(name = "pessoa")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pessoa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusPessoa status = StatusPessoa.PENDENTE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "congregacao_id", nullable = false)
    private Congregacao congregacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endereco_id")
    private Endereco endereco;

    // LGPD: responsável legal é obrigatório para menores (exigência validada no Service); nome e telefone andam juntos
    @Column(name = "responsavel_legal_nome", length = 150)
    private String responsavelLegalNome;

    @Column(name = "responsavel_legal_telefone", length = 20)
    private String responsavelLegalTelefone;

    @Column(name = "consentimento_lgpd_em")
    private Instant consentimentoLgpdEm;

    // trilha de aprovação; FK para app_user será adicionada na migration de app_user, por isso ainda é só o UUID
    @Column(name = "aprovado_por")
    private UUID aprovadoPor;

    @Column(name = "aprovado_em")
    private Instant aprovadoEm;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    private static final int IDADE_MAIORIDADE = 18;

    /** Anos completos em {@code hoje}. */
    public int idade(LocalDate hoje) {
        return Period.between(dataNascimento, hoje).getYears();
    }

    /** Menor de 18 anos: exige responsável legal e consentimento (LGPD). Independe da lista do corista. */
    public boolean menorDeIdade(LocalDate hoje) {
        return idade(hoje) < IDADE_MAIORIDADE;
    }

    public FaixaEtaria faixaEtaria(LocalDate hoje) {
        return menorDeIdade(hoje) ? FaixaEtaria.ADOLESCENTE : FaixaEtaria.JOVEM;
    }
}
