package com.registraai.registro_coristas_api.area.service;

import com.registraai.registro_coristas_api.area.dto.AreaRequest;
import com.registraai.registro_coristas_api.area.exception.AreaNaoEncontradaException;
import com.registraai.registro_coristas_api.area.exception.AreaNumeroDuplicadoException;
import com.registraai.registro_coristas_api.area.exception.AreaPossuiCongregacoesException;
import com.registraai.registro_coristas_api.area.model.Area;
import com.registraai.registro_coristas_api.area.repository.AreaRepository;
import com.registraai.registro_coristas_api.congregacao.repository.CongregacaoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AreaServiceTest {

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private CongregacaoRepository congregacaoRepository;

    @InjectMocks
    private AreaService areaService;

    @Test
    void criar_gravaAreaAtivaComNomeSemEspacosNasPontas() {
        when(areaRepository.existsByNumero(40)).thenReturn(false);
        when(areaRepository.save(any(Area.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Area area = areaService.criar(new AreaRequest(40, "  Área 40  "));

        assertThat(area.getNumero()).isEqualTo(40);
        assertThat(area.getNome()).isEqualTo("Área 40");
        assertThat(area.isAtiva()).isTrue();
    }

    @Test
    void criar_lancaExcecaoENaoGravaQuandoNumeroJaExiste() {
        when(areaRepository.existsByNumero(40)).thenReturn(true);

        assertThatThrownBy(() -> areaService.criar(new AreaRequest(40, "Área 40")))
                .isInstanceOf(AreaNumeroDuplicadoException.class);
        verify(areaRepository, never()).save(any());
    }

    @Test
    void buscarPorId_retornaAreaExistente() {
        UUID id = UUID.randomUUID();
        Area existente = Area.builder().id(id).numero(1).nome("Matriz").build();
        when(areaRepository.findById(id)).thenReturn(Optional.of(existente));

        assertThat(areaService.buscarPorId(id)).isSameAs(existente);
    }

    @Test
    void buscarPorId_lancaExcecaoQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(areaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> areaService.buscarPorId(id))
                .isInstanceOf(AreaNaoEncontradaException.class);
    }

    @Test
    void listar_semFiltroRetornaTodas() {
        List<Area> todas = List.of(Area.builder().numero(1).nome("Matriz").build());
        when(areaRepository.findAllByOrderByNumeroAsc()).thenReturn(todas);

        assertThat(areaService.listar(null)).isSameAs(todas);
        verify(areaRepository, never()).findAllByAtivaOrderByNumeroAsc(anyBoolean());
    }

    @Test
    void listar_comFiltroRepassaSituacaoAoRepositorio() {
        List<Area> inativas = List.of(Area.builder().numero(2).nome("Antiga").ativa(false).build());
        when(areaRepository.findAllByAtivaOrderByNumeroAsc(false)).thenReturn(inativas);

        assertThat(areaService.listar(false)).isSameAs(inativas);
        verify(areaRepository, never()).findAllByOrderByNumeroAsc();
    }

    @Test
    void atualizar_alteraNumeroENome() {
        UUID id = UUID.randomUUID();
        Area existente = Area.builder().id(id).numero(40).nome("Antigo").build();
        when(areaRepository.findById(id)).thenReturn(Optional.of(existente));
        when(areaRepository.existsByNumeroAndIdNot(41, id)).thenReturn(false);
        when(areaRepository.save(any(Area.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Area atualizada = areaService.atualizar(id, new AreaRequest(41, " Novo "));

        assertThat(atualizada.getId()).isEqualTo(id);
        assertThat(atualizada.getNumero()).isEqualTo(41);
        assertThat(atualizada.getNome()).isEqualTo("Novo");
    }

    @Test
    void atualizar_lancaExcecaoENaoGravaQuandoNumeroPertenceAOutraArea() {
        UUID id = UUID.randomUUID();
        Area existente = Area.builder().id(id).numero(40).nome("Área 40").build();
        when(areaRepository.findById(id)).thenReturn(Optional.of(existente));
        when(areaRepository.existsByNumeroAndIdNot(1, id)).thenReturn(true);

        assertThatThrownBy(() -> areaService.atualizar(id, new AreaRequest(1, "Área 40")))
                .isInstanceOf(AreaNumeroDuplicadoException.class);
        verify(areaRepository, never()).save(any());
    }

    @Test
    void atualizar_lancaExcecaoQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(areaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> areaService.atualizar(id, new AreaRequest(1, "Matriz")))
                .isInstanceOf(AreaNaoEncontradaException.class);
        verify(areaRepository, never()).save(any());
    }

    @Test
    void inativar_marcaAreaComoInativaSemRemoverDoBanco() {
        UUID id = UUID.randomUUID();
        Area existente = Area.builder().id(id).numero(40).nome("Área 40").build();
        when(areaRepository.findById(id)).thenReturn(Optional.of(existente));
        when(congregacaoRepository.countByAreaIdAndAtivaTrue(id)).thenReturn(0L);

        areaService.inativar(id);

        assertThat(existente.isAtiva()).isFalse();
        verify(areaRepository).save(existente);
        verify(areaRepository, never()).delete(any());
        verify(areaRepository, never()).deleteById(any());
    }

    @Test
    void inativar_lancaExcecaoENaoAlteraQuandoHaCongregacoesAtivasNaArea() {
        UUID id = UUID.randomUUID();
        Area existente = Area.builder().id(id).numero(40).nome("Área 40").build();
        when(areaRepository.findById(id)).thenReturn(Optional.of(existente));
        when(congregacaoRepository.countByAreaIdAndAtivaTrue(id)).thenReturn(13L);

        assertThatThrownBy(() -> areaService.inativar(id))
                .isInstanceOf(AreaPossuiCongregacoesException.class)
                .satisfies(excecao -> assertThat(((AreaPossuiCongregacoesException) excecao).getBody().getDetail())
                        .contains("área 40")
                        .contains("13 congregações ativas"));
        assertThat(existente.isAtiva()).isTrue();
        verify(areaRepository, never()).save(any());
    }

    @Test
    void inativar_mensagemUsaSingularQuandoHaUmaCongregacao() {
        var excecao = new AreaPossuiCongregacoesException(40, 1);

        assertThat(excecao.getBody().getDetail()).contains("existe 1 congregação ativa vinculada");
    }

    @Test
    void inativar_lancaExcecaoQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(areaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> areaService.inativar(id))
                .isInstanceOf(AreaNaoEncontradaException.class);
    }

    @Test
    void reativar_marcaAreaComoAtiva() {
        UUID id = UUID.randomUUID();
        Area inativa = Area.builder().id(id).numero(40).nome("Área 40").ativa(false).build();
        when(areaRepository.findById(id)).thenReturn(Optional.of(inativa));
        when(areaRepository.save(any(Area.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        assertThat(areaService.reativar(id).isAtiva()).isTrue();
    }
}
