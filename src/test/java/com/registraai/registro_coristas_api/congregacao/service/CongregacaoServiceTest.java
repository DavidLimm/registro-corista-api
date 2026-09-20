package com.registraai.registro_coristas_api.congregacao.service;

import com.registraai.registro_coristas_api.area.exception.AreaInativaException;
import com.registraai.registro_coristas_api.area.exception.AreaNaoEncontradaException;
import com.registraai.registro_coristas_api.area.model.Area;
import com.registraai.registro_coristas_api.area.service.AreaService;
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
    private AreaService areaService;

    @Mock
    private EnderecoService enderecoService;

    @InjectMocks
    private CongregacaoService congregacaoService;

    private final UUID areaId = UUID.randomUUID();
    private final Area area = Area.builder().id(areaId).numero(40).nome("Área 40").build();
    private final EnderecoRequest enderecoRequest =
            new EnderecoRequest("Rua da Aurora", "123", null, "Boa Vista", "Recife", "PE", "50050000");

    private Congregacao congregacaoExistente(UUID id, Area daArea, Endereco endereco) {
        return Congregacao.builder().id(id).area(daArea).endereco(endereco).nome("Sede").build();
    }

    // ---------- criar ----------

    @Test
    void criar_gravaCongregacaoAtivaComNomeSemEspacosEEndereco() {
        Endereco endereco = Endereco.builder().id(UUID.randomUUID()).logradouro("Rua da Aurora").build();
        when(areaService.buscarPorId(areaId)).thenReturn(area);
        when(congregacaoRepository.existsByAreaIdAndNomeIgnoreCase(areaId, "Sede")).thenReturn(false);
        when(enderecoService.criar(enderecoRequest)).thenReturn(endereco);
        when(congregacaoRepository.save(any(Congregacao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Congregacao congregacao = congregacaoService.criar(new CongregacaoRequest(areaId, "  Sede  ", enderecoRequest));

        assertThat(congregacao.getArea()).isSameAs(area);
        assertThat(congregacao.getNome()).isEqualTo("Sede");
        assertThat(congregacao.isAtiva()).isTrue();
        assertThat(congregacao.getEndereco()).isSameAs(endereco);
    }

    @Test
    void criar_semEnderecoNaoChamaEnderecoService() {
        when(areaService.buscarPorId(areaId)).thenReturn(area);
        when(congregacaoRepository.existsByAreaIdAndNomeIgnoreCase(areaId, "Sede")).thenReturn(false);
        when(congregacaoRepository.save(any(Congregacao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Congregacao congregacao = congregacaoService.criar(new CongregacaoRequest(areaId, "Sede", null));

        assertThat(congregacao.getEndereco()).isNull();
        verifyNoInteractions(enderecoService);
    }

    @Test
    void criar_lancaExcecaoENaoGravaQuandoAreaEstaInativa() {
        Area inativa = Area.builder().id(areaId).numero(40).nome("Área 40").ativa(false).build();
        when(areaService.buscarPorId(areaId)).thenReturn(inativa);

        assertThatThrownBy(() -> congregacaoService.criar(new CongregacaoRequest(areaId, "Sede", enderecoRequest)))
                .isInstanceOf(AreaInativaException.class);
        verify(congregacaoRepository, never()).save(any());
        verifyNoInteractions(enderecoService);
    }

    @Test
    void criar_propagaExcecaoQuandoAreaNaoExiste() {
        when(areaService.buscarPorId(areaId)).thenThrow(new AreaNaoEncontradaException(areaId));

        assertThatThrownBy(() -> congregacaoService.criar(new CongregacaoRequest(areaId, "Sede", null)))
                .isInstanceOf(AreaNaoEncontradaException.class);
        verify(congregacaoRepository, never()).save(any());
    }

    @Test
    void criar_lancaExcecaoENaoGravaEnderecoQuandoNomeJaExisteNaArea() {
        when(areaService.buscarPorId(areaId)).thenReturn(area);
        when(congregacaoRepository.existsByAreaIdAndNomeIgnoreCase(areaId, "Sede")).thenReturn(true);

        assertThatThrownBy(() -> congregacaoService.criar(new CongregacaoRequest(areaId, "Sede", enderecoRequest)))
                .isInstanceOf(CongregacaoNomeDuplicadoException.class);
        verify(congregacaoRepository, never()).save(any());
        verifyNoInteractions(enderecoService);
    }

    // ---------- buscar / listar ----------

    @Test
    void buscarPorId_retornaCongregacaoExistente() {
        UUID id = UUID.randomUUID();
        Congregacao existente = congregacaoExistente(id, area, null);
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
        List<Congregacao> lista = List.of(congregacaoExistente(UUID.randomUUID(), area, null));
        when(congregacaoRepository.findAll(ArgumentMatchers.<Specification<Congregacao>>any(), any(Sort.class)))
                .thenReturn(lista);

        assertThat(congregacaoService.listar(areaId, true)).isSameAs(lista);
        verify(congregacaoRepository).findAll(ArgumentMatchers.<Specification<Congregacao>>any(), eq(Sort.by("nome")));
    }

    // ---------- atualizar ----------

    @Test
    void atualizar_naMesmaAreaAlteraNomeEAtualizaEnderecoExistenteSemConsultarArea() {
        UUID id = UUID.randomUUID();
        UUID enderecoId = UUID.randomUUID();
        Endereco endereco = Endereco.builder().id(enderecoId).build();
        Congregacao existente = congregacaoExistente(id, area, endereco);
        when(congregacaoRepository.findById(id)).thenReturn(Optional.of(existente));
        when(congregacaoRepository.existsByAreaIdAndNomeIgnoreCaseAndIdNot(areaId, "Sede Nova", id)).thenReturn(false);
        when(congregacaoRepository.save(any(Congregacao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Congregacao atualizada = congregacaoService.atualizar(id, new CongregacaoRequest(areaId, " Sede Nova ", enderecoRequest));

        assertThat(atualizada.getNome()).isEqualTo("Sede Nova");
        assertThat(atualizada.getArea()).isSameAs(area);
        verify(enderecoService).atualizar(enderecoId, enderecoRequest);
        verify(enderecoService, never()).criar(any());
        verifyNoInteractions(areaService);
    }

    @Test
    void atualizar_semEnderecoNoRequestMantemOEnderecoAtual() {
        UUID id = UUID.randomUUID();
        Endereco endereco = Endereco.builder().id(UUID.randomUUID()).build();
        Congregacao existente = congregacaoExistente(id, area, endereco);
        when(congregacaoRepository.findById(id)).thenReturn(Optional.of(existente));
        when(congregacaoRepository.existsByAreaIdAndNomeIgnoreCaseAndIdNot(areaId, "Sede", id)).thenReturn(false);
        when(congregacaoRepository.save(any(Congregacao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Congregacao atualizada = congregacaoService.atualizar(id, new CongregacaoRequest(areaId, "Sede", null));

        assertThat(atualizada.getEndereco()).isSameAs(endereco);
        verifyNoInteractions(enderecoService);
    }

    @Test
    void atualizar_congregacaoSemEnderecoCriaEVinculaQuandoRequestTrazEndereco() {
        UUID id = UUID.randomUUID();
        Endereco novo = Endereco.builder().id(UUID.randomUUID()).build();
        Congregacao existente = congregacaoExistente(id, area, null);
        when(congregacaoRepository.findById(id)).thenReturn(Optional.of(existente));
        when(congregacaoRepository.existsByAreaIdAndNomeIgnoreCaseAndIdNot(areaId, "Sede", id)).thenReturn(false);
        when(enderecoService.criar(enderecoRequest)).thenReturn(novo);
        when(congregacaoRepository.save(any(Congregacao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Congregacao atualizada = congregacaoService.atualizar(id, new CongregacaoRequest(areaId, "Sede", enderecoRequest));

        assertThat(atualizada.getEndereco()).isSameAs(novo);
        verify(enderecoService, never()).atualizar(any(), any());
    }

    @Test
    void atualizar_remanejaParaOutraAreaAtivaValidandoNomeNoDestino() {
        UUID id = UUID.randomUUID();
        UUID destinoId = UUID.randomUUID();
        Area destino = Area.builder().id(destinoId).numero(41).nome("Área 41").build();
        Congregacao existente = congregacaoExistente(id, area, null);
        when(congregacaoRepository.findById(id)).thenReturn(Optional.of(existente));
        when(areaService.buscarPorId(destinoId)).thenReturn(destino);
        when(congregacaoRepository.existsByAreaIdAndNomeIgnoreCaseAndIdNot(destinoId, "Sede", id)).thenReturn(false);
        when(congregacaoRepository.save(any(Congregacao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Congregacao atualizada = congregacaoService.atualizar(id, new CongregacaoRequest(destinoId, "Sede", null));

        assertThat(atualizada.getArea()).isSameAs(destino);
    }

    @Test
    void atualizar_lancaExcecaoENaoGravaQuandoDestinoDoRemanejamentoEstaInativo() {
        UUID id = UUID.randomUUID();
        UUID destinoId = UUID.randomUUID();
        Area destinoInativo = Area.builder().id(destinoId).numero(41).nome("Área 41").ativa(false).build();
        Congregacao existente = congregacaoExistente(id, area, null);
        when(congregacaoRepository.findById(id)).thenReturn(Optional.of(existente));
        when(areaService.buscarPorId(destinoId)).thenReturn(destinoInativo);

        assertThatThrownBy(() -> congregacaoService.atualizar(id, new CongregacaoRequest(destinoId, "Sede", null)))
                .isInstanceOf(AreaInativaException.class);
        assertThat(existente.getArea()).isSameAs(area);
        verify(congregacaoRepository, never()).save(any());
    }

    @Test
    void atualizar_lancaExcecaoQuandoNomeJaExisteNaAreaDeDestino() {
        UUID id = UUID.randomUUID();
        Congregacao existente = congregacaoExistente(id, area, null);
        when(congregacaoRepository.findById(id)).thenReturn(Optional.of(existente));
        when(congregacaoRepository.existsByAreaIdAndNomeIgnoreCaseAndIdNot(areaId, "Filial", id)).thenReturn(true);

        assertThatThrownBy(() -> congregacaoService.atualizar(id, new CongregacaoRequest(areaId, "Filial", enderecoRequest)))
                .isInstanceOf(CongregacaoNomeDuplicadoException.class);
        verify(congregacaoRepository, never()).save(any());
        verifyNoInteractions(enderecoService);
    }

    @Test
    void atualizar_congregacaoInativaEmAreaInativaPodeSerRenomeadaSemExigirAreaAtiva() {
        UUID id = UUID.randomUUID();
        Area inativa = Area.builder().id(areaId).numero(40).nome("Área 40").ativa(false).build();
        Congregacao existente = congregacaoExistente(id, inativa, null);
        existente.setAtiva(false);
        when(congregacaoRepository.findById(id)).thenReturn(Optional.of(existente));
        when(congregacaoRepository.existsByAreaIdAndNomeIgnoreCaseAndIdNot(areaId, "Sede Antiga", id)).thenReturn(false);
        when(congregacaoRepository.save(any(Congregacao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Congregacao atualizada = congregacaoService.atualizar(id, new CongregacaoRequest(areaId, "Sede Antiga", null));

        assertThat(atualizada.getNome()).isEqualTo("Sede Antiga");
    }

    @Test
    void atualizar_lancaExcecaoQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(congregacaoRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> congregacaoService.atualizar(id, new CongregacaoRequest(areaId, "Sede", null)))
                .isInstanceOf(CongregacaoNaoEncontradaException.class);
        verify(congregacaoRepository, never()).save(any());
    }

    // ---------- inativar / reativar ----------

    @Test
    void inativar_marcaComoInativaSemRemoverDoBanco() {
        UUID id = UUID.randomUUID();
        Congregacao existente = congregacaoExistente(id, area, null);
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
    void reativar_marcaComoAtivaQuandoAreaEstaAtiva() {
        UUID id = UUID.randomUUID();
        Congregacao inativa = congregacaoExistente(id, area, null);
        inativa.setAtiva(false);
        when(congregacaoRepository.findById(id)).thenReturn(Optional.of(inativa));
        when(congregacaoRepository.save(any(Congregacao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        assertThat(congregacaoService.reativar(id).isAtiva()).isTrue();
    }

    @Test
    void reativar_lancaExcecaoENaoAlteraQuandoAreaEstaInativa() {
        UUID id = UUID.randomUUID();
        Area areaInativa = Area.builder().id(areaId).numero(40).nome("Área 40").ativa(false).build();
        Congregacao inativa = congregacaoExistente(id, areaInativa, null);
        inativa.setAtiva(false);
        when(congregacaoRepository.findById(id)).thenReturn(Optional.of(inativa));

        assertThatThrownBy(() -> congregacaoService.reativar(id))
                .isInstanceOf(AreaInativaException.class);
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
