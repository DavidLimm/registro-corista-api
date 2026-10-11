package com.registraai.registro_coristas_api.usuario.service;

import com.registraai.registro_coristas_api.agrupamento.model.Agrupamento;
import com.registraai.registro_coristas_api.agrupamento.model.Subdivisao;
import com.registraai.registro_coristas_api.agrupamento.model.TipoAgrupamento;
import com.registraai.registro_coristas_api.autenticacao.exception.ForaDoEscopoException;
import com.registraai.registro_coristas_api.autenticacao.service.UsuarioLogado;
import com.registraai.registro_coristas_api.congregacao.model.Congregacao;
import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.role.model.Role;
import com.registraai.registro_coristas_api.usuario.model.Usuario;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EscopoDeAcessoTest {

    @Mock
    private UsuarioLogado usuarioLogado;

    @Mock
    private EscopoService escopoService;

    @InjectMocks
    private EscopoDeAcesso escopo;

    private final Agrupamento dentro = Agrupamento.builder().id(UUID.randomUUID()).tipo(TipoAgrupamento.AREA).numero(40).build();
    private final Agrupamento fora = Agrupamento.builder().id(UUID.randomUUID()).tipo(TipoAgrupamento.AREA).numero(41).build();

    private Usuario logadoCom(String papel) {
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.getRoles().add(Role.builder().id(UUID.randomUUID()).nome(papel).descricao(papel).build());
        when(usuarioLogado.atual()).thenReturn(Optional.of(usuario));
        return usuario;
    }

    private Congregacao congregacaoEm(Agrupamento agrupamento) {
        Subdivisao subdivisao = Subdivisao.builder().id(UUID.randomUUID()).agrupamento(agrupamento).build();
        return Congregacao.builder().id(UUID.randomUUID()).subdivisao(subdivisao).nome("Sede").build();
    }

    @Test
    void adminEPastorAdmin_naoTemRestricao() {
        for (String papel : new String[]{"ADMIN", "PASTOR_ADMIN"}) {
            logadoCom(papel);
            assertThat(escopo.foraDoEscopo(fora.getId())).isFalse();
            assertThat(escopo.fora(congregacaoEm(fora))).isFalse();
        }
    }

    @Test
    void usuarioComum_soTemOsAgrupamentosVinculados() {
        Usuario lider = logadoCom("LIDER_MOCIDADE");
        when(escopoService.idsDosAgrupamentosDe(lider.getId())).thenReturn(Set.of(dentro.getId()));

        assertThat(escopo.fora(congregacaoEm(dentro))).isFalse();
        assertThat(escopo.fora(congregacaoEm(fora))).isTrue();
        assertThat(escopo.fora(Pessoa.builder().congregacao(congregacaoEm(fora)).build())).isTrue();
    }

    @Test
    void semUsuarioAutenticadoOuSemVinculo_naoEnxergaNada() {
        when(usuarioLogado.atual()).thenReturn(Optional.empty());
        assertThat(escopo.foraDoEscopo(dentro.getId())).isTrue();

        Usuario semVinculo = logadoCom("PASTOR");
        when(escopoService.idsDosAgrupamentosDe(semVinculo.getId())).thenReturn(Set.of());
        assertThat(escopo.foraDoEscopo(dentro.getId())).isTrue();
    }

    @Test
    void exigirDentro_lanca403QuandoForaEPassaQuandoDentro() {
        Usuario lider = logadoCom("LIDER_MOCIDADE");
        when(escopoService.idsDosAgrupamentosDe(lider.getId())).thenReturn(Set.of(dentro.getId()));

        escopo.exigirDentro(congregacaoEm(dentro));
        escopo.exigirDentro(congregacaoEm(dentro).getSubdivisao());
        assertThatThrownBy(() -> escopo.exigirDentro(congregacaoEm(fora))).isInstanceOf(ForaDoEscopoException.class);
        assertThatThrownBy(() -> escopo.exigirDentro(congregacaoEm(fora).getSubdivisao())).isInstanceOf(ForaDoEscopoException.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void restringirA_geraConjuncaoParaGlobalDisjuncaoSemVinculoEInParaOsVinculados() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Root<Object> raiz = mock(Root.class);
        CriteriaQuery<?> consulta = mock(CriteriaQuery.class);
        Expression<?> caminho = mock(Expression.class);
        Predicate in = mock(Predicate.class);
        Predicate conjuncao = mock(Predicate.class);
        Predicate disjuncao = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(conjuncao);
        when(cb.disjunction()).thenReturn(disjuncao);
        when(caminho.in(any(Set.class))).thenReturn(in);

        logadoCom("ADMIN");
        Specification<Object> global = escopo.restringirA(r -> caminho);
        assertThat(global.toPredicate(raiz, consulta, cb)).isSameAs(conjuncao);

        Usuario semVinculo = logadoCom("PASTOR");
        when(escopoService.idsDosAgrupamentosDe(semVinculo.getId())).thenReturn(Set.of());
        assertThat(escopo.<Object>restringirA(r -> caminho).toPredicate(raiz, consulta, cb)).isSameAs(disjuncao);

        Usuario lider = logadoCom("LIDER_MOCIDADE");
        when(escopoService.idsDosAgrupamentosDe(lider.getId())).thenReturn(Set.of(dentro.getId()));
        assertThat(escopo.<Object>restringirA(r -> caminho).toPredicate(raiz, consulta, cb)).isSameAs(in);
        verify(caminho).in(Set.of(dentro.getId()));
    }
}
