package com.registraai.registro_coristas_api.corista.model;

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
import jakarta.persistence.OneToOne;
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
 * Especialização 1:1 de {@link Pessoa} com os campos exclusivos de corista. Nome, nascimento, endereço, congregação
 * e status ficam em {@code Pessoa}; a faixa etária real é sempre derivada de {@code pessoa.dataNascimento}.
 */
@Entity
@Table(name = "corista")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Corista {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pessoa_id", nullable = false, unique = true)
    private Pessoa pessoa;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_voz", nullable = false, length = 20)
    private TipoVoz tipoVoz;

    @Enumerated(EnumType.STRING)
    @Column(name = "tamanho_camisa", nullable = false, length = 20)
    private TamanhoCamisa tamanhoCamisa;

    @Column(length = 100)
    private String ocupacao;

    /** Gravada (não derivada): por padrão segue a idade; ver {@link ListaClassificacao}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "lista_classificacao", nullable = false, length = 20)
    private ListaClassificacao listaClassificacao;

    // trilha da promoção antecipada (ADOLESCENTE -> JOVEM); FK para app_user virá na migration de app_user
    @Column(name = "promovido_por")
    private UUID promovidoPor;

    @Column(name = "promovido_em")
    private Instant promovidoEm;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;
}
