package com.registraai.registro_coristas_api.congregacao.service;

import com.registraai.registro_coristas_api.agrupamento.exception.SubdivisaoInativaException;
import com.registraai.registro_coristas_api.agrupamento.exception.SubdivisaoNaoEncontradaException;
import com.registraai.registro_coristas_api.agrupamento.model.Subdivisao;
import com.registraai.registro_coristas_api.suporte.HierarquiaTeste;
import com.registraai.registro_coristas_api.agrupamento.service.SubdivisaoService;
import com.registraai.registro_coristas_api.congregacao.dto.CongregacaoRequest;
import com.registraai.registro_coristas_api.congregacao.exception.CongregacaoNaoEncontradaException;
import com.registraai.registro_coristas_api.congregacao.exception.CongregacaoNomeDuplicadoException;
import com.registraai.registro_coristas_api.congregacao.model.Congregacao;
import com.registraai.registro_coristas_api.congregacao.repository.CongregacaoRepository;
import com.registraai.registro_coristas_api.endereco.dto.EnderecoRequest;
import com.registraai.registro_coristas_api.endereco.model.Endereco;
import com.registraai.registro_coristas_api.endereco.service.EnderecoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CongregacaoServiceTest {

    @Mock
    private CongregacaoRepository congregacaoRepository;

    @Mock
    private SubdivisaoService subdivisaoService;

    @Mock
    private EnderecoService enderecoService;

    @InjectMocks
    private CongregacaoService congregacaoService;

    private final UUID subdivisaoId = UUID.randomUUID();
    private final Subdivisao subdivisao = HierarquiaTeste.subdivisaoDaArea40(UUID.randomUUID(), subdivisaoId);
    private final EnderecoRequest enderecoRequest =
            new EnderecoRequest("Rua da Aurora", "123", null, "Boa Vista", "Recife", "PE", "50050000");

    private Congregacao congregacaoExistente(UUID id, Subdivisao daSubdivisao, Endereco endereco) {
        return Congregacao.builder().id(id).subdivisao(daSubdivisao).endereco(endereco).nome("Sede").build();
    }

    // ---------- criar ----------

    @Test
    void criar_gravaCongregacaoAtivaComNomeSemEspacosEEndereco() {
        Endereco endereco = Endereco.builder().id(UUID.randomUUID()).logradouro("Rua da Aurora").build();
        when(subdivisaoService.buscarAtivaParaVinculo(subdivisaoId)).thenReturn(subdivisao);
        when(congregacaoRepository.existsBySubdivisaoIdAndNomeIgnoreCase(subdivisaoId, "Sede")).thenReturn(false);
        when(enderecoService.criar(enderecoRequest)).thenReturn(endereco);
        when(congregacaoRepository.save(any(Congregacao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Congregacao congregacao = congregacaoService.criar(new CongregacaoRequest(subdivisaoId, "  Sede  ", enderecoRequest));

        assertThat(congregacao.getSubdivisao()).isSameAs(subdivisao);
        assertThat(congregacao.getNome()).isEqualTo("Sede");
        assertThat(congregacao.isAtiva()).isTrue();
        assertThat(congregacao.getEndereco()).isSameAs(endereco);
    }

    @Test
    void criar_semEnderecoNaoChamaEnderecoService() {
        when(subdivisaoService.buscarAtivaParaVinculo(subdivisaoId)).thenReturn(subdivisao);
        when(congregacaoRepository.existsBySubdivisaoIdAndNomeIgnoreCase(subdivisaoId, "Sede")).thenReturn(false);
        when(congregacaoRepository.save(any(Congregacao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Congregacao congregacao = congregacaoService.criar(new CongregacaoRequest(subdivisaoId, "Sede", null));

        assertThat(congregacao.getEndereco()).isNull();
        verifyNoInteractions(enderecoService);
    }

    @Test
    void criar_lancaExcecaoENaoGravaQuandoSubdivisaoEstaInativa() {
        Subdivisao inativa = HierarquiaTeste.subdivisaoDaArea40(UUID.randomUUID(), subdivisaoId);
        inativa.setAtivo(false);
        when(subdivisaoService.buscarAtivaParaVinculo(subdivisaoId)).thenThrow(new SubdivisaoInativaException());

        assertThatThrownBy(() -> congregacaoService.criar(new CongregacaoRequest(subdivisaoId, "Sede", enderecoRequest)))
                .isInstanceOf(SubdivisaoInativaException.class);
        verify(congregacaoRepository, never()).save(any());
        verifyNoInteractions(enderecoService);
    }

    @Test
    void criar_propagaExcecaoQuandoSubdivisaoNaoExiste() {
        when(subdivisaoService.buscarAtivaParaVinculo(subdivisaoId)).thenThrow(new SubdivisaoNaoEncontradaException(subdivisaoId));

        assertThatThrownBy(() -> congregacaoService.criar(new CongregacaoRequest(subdivisaoId, "Sede", null)))
                .isInstanceOf(SubdivisaoNaoEncontradaException.class);
        verify(congregacaoRepository, never()).save(any());
    }

    @Test
    void criar_lancaExcecaoENaoGravaEnderecoQuandoNomeJaExisteNaSubdivisao() {
        when(subdivisaoService.buscarAtivaParaVinculo(subdivisaoId)).thenReturn(subdivisao);
        when(congregacaoRepository.existsBySubdivisaoIdAndNomeIgnoreCase(subdivisaoId, "Sede")).thenReturn(true);

        assertThatThrownBy(() -> congregacaoService.criar(new CongregacaoRequest(subdivisaoId, "Sede", enderecoRequest)))
                .isInstanceOf(CongregacaoNomeDuplicadoException.class);
        verify(congregacaoRepository, never()).save(any());
        verifyNoInteractions(enderecoService);
    }

    // ---------- buscar / listar ----------

    @Test
    void buscarPorId_retornaCongregacaoExistente() {
        UUID id = UUID.randomUUID();
        Congregacao existente = congregacaoExistente(id, subdivisao, null);
        when(congregacaoRepository.findById(id)).thenReturn(Optional.of(existente));

        assertThat(congregacaoService.buscarPorId(id)).isSameAs(existente);
    }

    @Test
    void buscarPorId_lancaExcecaoQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(congregacaoRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> congregacaoService.buscarPorId(id))
                .isInstanceOf(CongregacaoNaoEncontradaException.class);
    }

    @Test
    void listar_delegaAoRepositorioOrdenandoPorNome() {
        List<Congregacao> lista = List.of(congregacaoExistente(UUID.randomUUID(), subdivisao, null));
        when(congregacaoRepository.findAll(ArgumentMatchers.<Specification<Congregacao>>any(), any(Sort.class)))
                .thenReturn(lista);

        assertThat(congregacaoService.listar(null, subdivisaoId, true)).isSameAs(lista);
        verify(congregacaoRepository).findAll(ArgumentMatchers.<Specification<Congregacao>>any(), eq(Sort.by("nome")));
    }

    // ---------- atualizar ----------

    @Test
    void atualizar_naMesmaSubdivisaoAlteraNomeEAtualizaEnderecoExistenteSemConsultarSubdivisao() {
        UUID id = UUID.randomUUID();
        UUID enderecoId = UUID.randomUUID();
        Endereco endereco = Endereco.builder().id(enderecoId).build();
        Congregacao existente = congregacaoExistente(id, subdivisao, endereco);
        when(congregacaoRepository.findById(id)).thenReturn(Optional.of(existente));
        when(congregacaoRepository.existsBySubdivisaoIdAndNomeIgnoreCaseAndIdNot(subdivisaoId, "Sede Nova", id)).thenReturn(false);
        when(congregacaoRepository.save(any(Congregacao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Congregacao atualizada = congregacaoService.atualizar(id, new CongregacaoRequest(subdivisaoId, " Sede Nova ", enderecoRequest));

        assertThat(atualizada.getNome()).isEqualTo("Sede Nova");
        assertThat(atualizada.getSubdivisao()).isSameAs(subdivisao);
        verify(enderecoService).atualizar(enderecoId, enderecoRequest);
        verify(enderecoService, never()).criar(any());
        verifyNoInteractions(subdivisaoService);
    }

    @Test
    void atualizar_semEnderecoNoRequestMantemOEnderecoAtual() {
        UUID id = UUID.randomUUID();
        Endereco endereco = Endereco.builder().id(UUID.randomUUID()).build();
        Congregacao existente = congregacaoExistente(id, subdivisao, endereco);
        when(congregacaoRepository.findById(id)).thenReturn(Optional.of(existente));
        when(congregacaoRepository.existsBySubdivisaoIdAndNomeIgnoreCaseAndIdNot(subdivisaoId, "Sede", id)).thenReturn(false);
        when(congregacaoRepository.save(any(Congregacao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Congregacao atualizada = congregacaoService.atualizar(id, new CongregacaoRequest(subdivisaoId, "Sede", null));

        assertThat(atualizada.getEndereco()).isSameAs(endereco);
        verifyNoInteractions(enderecoService);
    }

    @Test
    void atualizar_congregacaoSemEnderecoCriaEVinculaQuandoRequestTrazEndereco() {
        UUID id = UUID.randomUUID();
        Endereco novo = Endereco.builder().id(UUID.randomUUID()).build();
        Congregacao existente = congregacaoExistente(id, subdivisao, null);
        when(congregacaoRepository.findById(id)).thenReturn(Optional.of(existente));
        when(congregacaoRepository.existsBySubdivisaoIdAndNomeIgnoreCaseAndIdNot(subdivisaoId, "Sede", id)).thenReturn(false);
        when(enderecoService.criar(enderecoRequest)).thenReturn(novo);
        when(congregacaoRepository.save(any(Congregacao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Congregacao atualizada = congregacaoService.atualizar(id, new CongregacaoRequest(subdivisaoId, "Sede", enderecoRequest));

        assertThat(atualizada.getEndereco()).isSameAs(novo);
        verify(enderecoService, never()).atualizar(any(), any());
    }

    @Test
    void atualizar_remanejaParaOutraSubdivisaoAtivaValidandoNomeNoDestino() {
        UUID id = UUID.randomUUID();
        UUID destinoId = UUID.randomUUID();
        Subdivisao destino = HierarquiaTeste.subdivisaoDaArea40(UUID.randomUUID(), destinoId);
        Congregacao existente = congregacaoExistente(id, subdivisao, null);
        when(congregacaoRepository.findById(id)).thenReturn(Optional.of(existente));
        when(subdivisaoService.buscarAtivaParaVinculo(destinoId)).thenReturn(destino);
        when(congregacaoRepository.existsBySubdivisaoIdAndNomeIgnoreCaseAndIdNot(destinoId, "Sede", id)).thenReturn(false);
        when(congregacaoRepository.save(any(Congregacao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Congregacao atualizada = congregacaoService.atualizar(id, new CongregacaoRequest(destinoId, "Sede", null));

        assertThat(atualizada.getSubdivisao()).isSameAs(destino);
    }

    @Test
    void atualizar_lancaExcecaoENaoGravaQuandoDestinoDoRemanejamentoEstaInativo() {
        UUID id = UUID.randomUUID();
        UUID destinoId = UUID.randomUUID();
        // o destino inativo é recusado pelo SubdivisaoService, que é quem sabe se está ativa
        Congregacao existente = congregacaoExistente(id, subdivisao, null);
        when(congregacaoRepository.findById(id)).thenReturn(Optional.of(existente));
        when(subdivisaoService.buscarAtivaParaVinculo(destinoId)).thenThrow(new SubdivisaoInativaException());

        assertThatThrownBy(() -> congregacaoService.atualizar(id, new CongregacaoRequest(destinoId, "Sede", null)))
                .isInstanceOf(SubdivisaoInativaException.class);
        assertThat(existente.getSubdivisao()).isSameAs(subdivisao);
        verify(congregacaoRepository, never()).save(any());
    }

    @Test
    void atualizar_lancaExcecaoQuandoNomeJaExisteNaSubdivisaoDeDestino() {
        UUID id = UUID.randomUUID();
        Congregacao existente = congregacaoExistente(id, subdivisao, null);
        when(congregacaoRepository.findById(id)).thenReturn(Optional.of(existente));
        when(congregacaoRepository.existsBySubdivisaoIdAndNomeIgnoreCaseAndIdNot(subdivisaoId, "Filial", id)).thenReturn(true);

        assertThatThrownBy(() -> congregacaoService.atualizar(id, new CongregacaoRequest(subdivisaoId, "Filial", enderecoRequest)))
                .isInstanceOf(CongregacaoNomeDuplicadoException.class);
        verify(congregacaoRepository, never()).save(any());
        verifyNoInteractions(enderecoService);
    }

    @Test
    void atualizar_congregacaoInativaEmSubdivisaoInativaPodeSerRenomeadaSemExigirSubdivisaoAtiva() {
        UUID id = UUID.randomUUID();
        Subdivisao inativa = HierarquiaTeste.subdivisaoDaArea40(UUID.randomUUID(), subdivisaoId);
        inativa.setAtivo(false);
        Congregacao existente = congregacaoExistente(id, inativa, null);
        existente.setAtiva(false);
        when(congregacaoRepository.findById(id)).thenReturn(Optional.of(existente));
        when(congregacaoRepository.existsBySubdivisaoIdAndNomeIgnoreCaseAndIdNot(subdivisaoId, "Sede Antiga", id)).thenReturn(false);
        when(congregacaoRepository.save(any(Congregacao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Congregacao atualizada = congregacaoService.atualizar(id, new CongregacaoRequest(subdivisaoId, "Sede Antiga", null));

        assertThat(atualizada.getNome()).isEqualTo("Sede Antiga");
    }

    @Test
    void atualizar_lancaExcecaoQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(congregacaoRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> congregacaoService.atualizar(id, new CongregacaoRequest(subdivisaoId, "Sede", null)))
                .isInstanceOf(CongregacaoNaoEncontradaException.class);
        verify(congregacaoRepository, never()).save(any());
    }

    // ---------- inativar / reativar ----------

    @Test
    void inativar_marcaComoInativaSemRemoverDoBanco() {
        UUID id = UUID.randomUUID();
        Congregacao existente = congregacaoExistente(id, subdivisao, null);
        when(congregacaoRepository.findById(id)).thenReturn(Optional.of(existente));

        congregacaoService.inativar(id);

        assertThat(existente.isAtiva()).isFalse();
        verify(congregacaoRepository).save(existente);
        verify(congregacaoRepository, never()).delete(any(Congregacao.class));
        verify(congregacaoRepository, never()).deleteById(any());
    }

    @Test
    void inativar_lancaExcecaoQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(congregacaoRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> congregacaoService.inativar(id))
                .isInstanceOf(CongregacaoNaoEncontradaException.class);
    }

    @Test
    void reativar_marcaComoAtivaQuandoSubdivisaoEAgrupamentoEstaoAtivos() {
        UUID id = UUID.randomUUID();
        Congregacao inativa = congregacaoExistente(id, subdivisao, null);
        inativa.setAtiva(false);
        when(congregacaoRepository.findById(id)).thenReturn(Optional.of(inativa));
        when(congregacaoRepository.save(any(Congregacao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        assertThat(congregacaoService.reativar(id).isAtiva()).isTrue();
    }

    @Test
    void reativar_lancaExcecaoENaoAlteraQuandoSubdivisaoEstaInativa() {
        UUID id = UUID.randomUUID();
        Subdivisao subdivisaoInativa = HierarquiaTeste.subdivisaoDaArea40(UUID.randomUUID(), subdivisaoId);
        subdivisaoInativa.setAtivo(false);
        Congregacao inativa = congregacaoExistente(id, subdivisaoInativa, null);
        inativa.setAtiva(false);
        when(congregacaoRepository.findById(id)).thenReturn(Optional.of(inativa));

        assertThatThrownBy(() -> congregacaoService.reativar(id))
                .isInstanceOf(SubdivisaoInativaException.class);
        assertThat(inativa.isAtiva()).isFalse();
        verify(congregacaoRepository, never()).save(any());
    }

    @Test
    void reativar_lancaExcecaoQuandoOAgrupamentoDaSubdivisaoEstaInativo() {
        UUID id = UUID.randomUUID();
        subdivisao.getAgrupamento().setAtivo(false);
        Congregacao inativa = congregacaoExistente(id, subdivisao, null);
        inativa.setAtiva(false);
        when(congregacaoRepository.findById(id)).thenReturn(Optional.of(inativa));

        assertThatThrownBy(() -> congregacaoService.reativar(id))
                .isInstanceOf(SubdivisaoInativaException.class);
        assertThat(inativa.isAtiva()).isFalse();
        verify(congregacaoRepository, never()).save(any());
    }

    @Test
    void reativar_lancaExcecaoQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(congregacaoRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> congregacaoService.reativar(id))
                .isInstanceOf(CongregacaoNaoEncontradaException.class);
    }
}
