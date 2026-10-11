package com.registraai.registro_coristas_api.usuario.service;

import com.registraai.registro_coristas_api.agrupamento.exception.AgrupamentoNaoEncontradoException;
import com.registraai.registro_coristas_api.agrupamento.model.Agrupamento;
import com.registraai.registro_coristas_api.agrupamento.model.Subdivisao;
import com.registraai.registro_coristas_api.agrupamento.model.TipoAgrupamento;
import com.registraai.registro_coristas_api.agrupamento.repository.AgrupamentoRepository;
import com.registraai.registro_coristas_api.autenticacao.service.UsuarioLogado;
import com.registraai.registro_coristas_api.congregacao.model.Congregacao;
import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.role.model.Role;
import com.registraai.registro_coristas_api.usuario.exception.EscopoInvalidoException;
import com.registraai.registro_coristas_api.usuario.exception.UsuarioNaoEncontradoException;
import com.registraai.registro_coristas_api.usuario.model.Usuario;
import com.registraai.registro_coristas_api.usuario.model.UsuarioAgrupamento;
import com.registraai.registro_coristas_api.usuario.repository.UsuarioAgrupamentoRepository;
import com.registraai.registro_coristas_api.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EscopoServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AgrupamentoRepository agrupamentoRepository;

    @Mock
    private UsuarioAgrupamentoRepository vinculoRepository;

    @Mock
    private UsuarioLogado usuarioLogado;

    @InjectMocks
    private EscopoService escopoService;

    private Agrupamento area(int numero) {
        return Agrupamento.builder().id(UUID.randomUUID()).tipo(TipoAgrupamento.AREA).numero(numero).build();
    }

    private Agrupamento campo(String nome) {
        return Agrupamento.builder().id(UUID.randomUUID()).tipo(TipoAgrupamento.CAMPO_MISSIONARIO).nome(nome).pais(nome).build();
    }

    private Usuario usuarioCom(String... papeis) {
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        for (String papel : papeis) {
            usuario.getRoles().add(Role.builder().id(UUID.randomUUID()).nome(papel).descricao(papel).build());
        }
        org.mockito.Mockito.lenient().when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        return usuario;
    }

    private void agrupamentosExistem(Agrupamento... agrupamentos) {
        when(agrupamentoRepository.findAllById(any())).thenReturn(List.of(agrupamentos));
    }

    private Set<UUID> ids(Agrupamento... agrupamentos) {
        return java.util.Arrays.stream(agrupamentos).map(Agrupamento::getId).collect(java.util.stream.Collectors.toSet());
    }

    private void jaVinculado(Usuario usuario, Agrupamento... agrupamentos) {
        List<UsuarioAgrupamento> vinculos = java.util.Arrays.stream(agrupamentos)
                .map(a -> UsuarioAgrupamento.builder().usuario(usuario).agrupamento(a).build()).toList();
        when(vinculoRepository.findAllByUsuarioId(usuario.getId())).thenReturn(vinculos);
    }

    // ---------- definir ----------

    @Test
    void definir_adminEPastorAdminSaoGlobaisENaoRecebemVinculos() {
        Usuario admin = usuarioCom("ADMIN");
        Usuario pastorAdmin = usuarioCom("PASTOR_ADMIN");

        assertThatThrownBy(() -> escopoService.definir(admin.getId(), Set.of(UUID.randomUUID())))
                .isInstanceOf(EscopoInvalidoException.class);
        assertThatThrownBy(() -> escopoService.definir(pastorAdmin.getId(), Set.of()))
                .isInstanceOf(EscopoInvalidoException.class);
        verify(vinculoRepository, never()).saveAll(any());
    }

    @Test
    void definir_papelComumExigeExatamenteUmAgrupamento() {
        Usuario lider = usuarioCom("LIDER_MOCIDADE");
        Agrupamento a40 = area(40);
        Agrupamento a41 = area(41);
        agrupamentosExistem(a40, a41);

        assertThatThrownBy(() -> escopoService.definir(lider.getId(), ids(a40, a41)))
                .isInstanceOf(EscopoInvalidoException.class);

        agrupamentosExistem();
        assertThatThrownBy(() -> escopoService.definir(lider.getId(), Set.of()))
                .isInstanceOf(EscopoInvalidoException.class);
    }

    @Test
    void definir_papelComumGravaOVinculoComAutorNaTrilha() {
        Usuario lider = usuarioCom("LIDER_MOCIDADE");
        Usuario autor = new Usuario();
        autor.setId(UUID.randomUUID());
        when(usuarioLogado.atual()).thenReturn(Optional.of(autor));
        Agrupamento a40 = area(40);
        agrupamentosExistem(a40);
        when(vinculoRepository.findAllByUsuarioId(lider.getId())).thenReturn(List.of());

        escopoService.definir(lider.getId(), ids(a40));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UsuarioAgrupamento>> captor = ArgumentCaptor.forClass(List.class);
        verify(vinculoRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getAgrupamento()).isSameAs(a40);
        assertThat(captor.getValue().get(0).getVinculadoPor()).isEqualTo(autor.getId());
    }

    @Test
    void definir_pastorPodeTerAteDoisAgrupamentosDeQuaisquerTipos() {
        Usuario pastor = usuarioCom("PASTOR");
        Agrupamento filial = Agrupamento.builder().id(UUID.randomUUID()).tipo(TipoAgrupamento.FILIAL).nome("Petrolina").build();
        Agrupamento setor = Agrupamento.builder().id(UUID.randomUUID()).tipo(TipoAgrupamento.SETOR).numero(1).nome("Cabo").build();
        when(usuarioLogado.atual()).thenReturn(Optional.empty());
        agrupamentosExistem(filial, setor);
        when(vinculoRepository.findAllByUsuarioId(pastor.getId())).thenReturn(List.of());

        assertThat(escopoService.definir(pastor.getId(), ids(filial, setor))).hasSize(2);
    }

    @Test
    void definir_pastorNaoPodeTerTresAgrupamentos() {
        Usuario pastor = usuarioCom("PASTOR");
        Agrupamento a = area(1);
        Agrupamento b = area(2);
        Agrupamento c = area(3);
        agrupamentosExistem(a, b, c);

        assertThatThrownBy(() -> escopoService.definir(pastor.getId(), ids(a, b, c)))
                .isInstanceOf(EscopoInvalidoException.class)
                .hasMessageContaining("no máximo 2");
    }

    @Test
    void definir_pastorDeCampoMissionarioFicaApenasNele() {
        Usuario pastor = usuarioCom("PASTOR");
        Agrupamento argentina = campo("Argentina");
        Agrupamento a40 = area(40);
        agrupamentosExistem(argentina, a40);

        assertThatThrownBy(() -> escopoService.definir(pastor.getId(), ids(argentina, a40)))
                .isInstanceOf(EscopoInvalidoException.class)
                .hasMessageContaining("Campo Missionário");
    }

    @Test
    void definir_pastorPodeFicarSemVinculosEIssoRemoveOsAntigos() {
        Usuario pastor = usuarioCom("PASTOR");
        Agrupamento a40 = area(40);
        jaVinculado(pastor, a40);
        agrupamentosExistem();

        assertThat(escopoService.definir(pastor.getId(), Set.of())).isEmpty();

        verify(vinculoRepository).removerDoUsuario(pastor.getId(), List.of(a40.getId()));
    }

    @Test
    void definir_trocarOAgrupamentoRemoveOAntigoEAdicionaSoOQueFalta() {
        Usuario lider = usuarioCom("LIDER_MOCIDADE");
        Agrupamento antigo = area(40);
        Agrupamento novo = area(41);
        jaVinculado(lider, antigo);
        agrupamentosExistem(novo);
        when(usuarioLogado.atual()).thenReturn(Optional.empty());

        escopoService.definir(lider.getId(), ids(novo));

        verify(vinculoRepository).removerDoUsuario(lider.getId(), List.of(antigo.getId()));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UsuarioAgrupamento>> captor = ArgumentCaptor.forClass(List.class);
        verify(vinculoRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(UsuarioAgrupamento::getAgrupamento).containsExactly(novo);
    }

    @Test
    void definir_recusaAgrupamentoInativoEInexistente() {
        Usuario lider = usuarioCom("LIDER_MOCIDADE");
        Agrupamento inativo = area(40);
        inativo.setAtivo(false);
        agrupamentosExistem(inativo);

        assertThatThrownBy(() -> escopoService.definir(lider.getId(), ids(inativo)))
                .isInstanceOf(EscopoInvalidoException.class)
                .hasMessageContaining("inativo");

        agrupamentosExistem();
        assertThatThrownBy(() -> escopoService.definir(lider.getId(), Set.of(UUID.randomUUID())))
                .isInstanceOf(AgrupamentoNaoEncontradoException.class);
    }

    @Test
    void definir_usuarioInexistente_lancaNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> escopoService.definir(id, Set.of())).isInstanceOf(UsuarioNaoEncontradoException.class);
    }

    // ---------- ajustarAoPapel ----------

    private Usuario comCongregacaoNo(Usuario usuario, Agrupamento agrupamento) {
        Subdivisao subdivisao = Subdivisao.builder().id(UUID.randomUUID()).agrupamento(agrupamento).build();
        Congregacao congregacao = Congregacao.builder().id(UUID.randomUUID()).subdivisao(subdivisao).nome("Sede").build();
        usuario.setPessoa(Pessoa.builder().id(UUID.randomUUID()).congregacao(congregacao).build());
        return usuario;
    }

    @Test
    void ajustarAoPapel_globalPerdeOsVinculos() {
        Usuario admin = usuarioCom("ADMIN");

        escopoService.ajustarAoPapel(admin);

        verify(vinculoRepository).removerTodosDoUsuario(admin.getId());
    }

    @Test
    void ajustarAoPapel_semVinculoRecebeOAgrupamentoDaCongregacaoDaPessoa() {
        Usuario corista = comCongregacaoNo(usuarioCom("CORISTA_JOVENS"), area(40));
        Agrupamento daCongregacao = corista.getPessoa().getCongregacao().getSubdivisao().getAgrupamento();
        when(vinculoRepository.findAllByUsuarioId(corista.getId())).thenReturn(List.of());
        when(usuarioLogado.atual()).thenReturn(Optional.empty());

        escopoService.ajustarAoPapel(corista);

        ArgumentCaptor<UsuarioAgrupamento> captor = ArgumentCaptor.forClass(UsuarioAgrupamento.class);
        verify(vinculoRepository).save(captor.capture());
        assertThat(captor.getValue().getAgrupamento()).isSameAs(daCongregacao);
        assertThat(captor.getValue().getVinculadoPor()).isNull();
    }

    @Test
    void ajustarAoPapel_pastorSemVinculoNaoRecebeNada() {
        Usuario pastor = usuarioCom("PASTOR");
        when(vinculoRepository.findAllByUsuarioId(pastor.getId())).thenReturn(List.of());

        escopoService.ajustarAoPapel(pastor);

        verify(vinculoRepository, never()).save(any());
    }

    @Test
    void ajustarAoPapel_barraATrocaQuandoHaMaisVinculosDoQueOPapelNovoPermite() {
        Usuario lider = usuarioCom("LIDER_MOCIDADE");
        jaVinculado(lider, area(1), area(2));

        assertThatThrownBy(() -> escopoService.ajustarAoPapel(lider)).isInstanceOf(EscopoInvalidoException.class);
        verify(vinculoRepository, never()).removerDoUsuario(any(), anyCollection());
    }

    @Test
    void ajustarAoPapel_mantemQuandoJaEstaCoerente() {
        Usuario lider = usuarioCom("LIDER_MOCIDADE");
        jaVinculado(lider, area(1));

        escopoService.ajustarAoPapel(lider);

        verify(vinculoRepository, never()).save(any());
        verify(vinculoRepository, never()).removerTodosDoUsuario(any());
    }

    // ---------- consultas ----------

    @Test
    void ehGlobal_eIdsDosAgrupamentos() {
        Usuario pastorAdmin = usuarioCom("PASTOR_ADMIN");
        Usuario lider = usuarioCom("LIDER_MOCIDADE");
        Agrupamento a40 = area(40);
        jaVinculado(lider, a40);

        assertThat(escopoService.ehGlobal(pastorAdmin.getId())).isTrue();
        assertThat(escopoService.ehGlobal(lider.getId())).isFalse();
        assertThat(escopoService.idsDosAgrupamentosDe(lider.getId())).containsExactly(a40.getId());
        Collection<UUID> vazio = escopoService.idsDosAgrupamentosDe(UUID.randomUUID());
        assertThat(vazio).isEmpty();
    }
}
