package com.registraai.registro_coristas_api.agrupamento.service;

import com.registraai.registro_coristas_api.agrupamento.dto.SubdivisaoRequest;
import com.registraai.registro_coristas_api.agrupamento.exception.AgrupamentoDuplicadoException;
import com.registraai.registro_coristas_api.agrupamento.exception.AgrupamentoInativoException;
import com.registraai.registro_coristas_api.agrupamento.exception.AgrupamentoInvalidoException;
import com.registraai.registro_coristas_api.agrupamento.exception.AgrupamentoNaoEncontradoException;
import com.registraai.registro_coristas_api.agrupamento.exception.AgrupamentoPossuiCongregacoesException;
import com.registraai.registro_coristas_api.agrupamento.exception.SubdivisaoNaoEncontradaException;
import com.registraai.registro_coristas_api.agrupamento.model.Agrupamento;
import com.registraai.registro_coristas_api.agrupamento.model.Subdivisao;
import com.registraai.registro_coristas_api.agrupamento.repository.AgrupamentoRepository;
import com.registraai.registro_coristas_api.agrupamento.repository.SubdivisaoRepository;
import com.registraai.registro_coristas_api.congregacao.repository.CongregacaoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static com.registraai.registro_coristas_api.agrupamento.model.TipoAgrupamento.AREA;
import static com.registraai.registro_coristas_api.agrupamento.model.TipoAgrupamento.CAMPO_MISSIONARIO;
import static com.registraai.registro_coristas_api.agrupamento.model.TipoAgrupamento.FILIAL;
import static com.registraai.registro_coristas_api.agrupamento.model.TipoAgrupamento.SETOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubdivisaoServiceTest {

    @Mock
    private AgrupamentoRepository agrupamentoRepository;

    @Mock
    private SubdivisaoRepository subdivisaoRepository;

    @Mock
    private CongregacaoRepository congregacaoRepository;

    @InjectMocks
    private SubdivisaoService subdivisaoService;

    private final UUID agrupamentoId = UUID.randomUUID();

    private Agrupamento agrupamentoExistente(Agrupamento agrupamento) {
        agrupamento.setId(agrupamentoId);
        when(agrupamentoRepository.findById(agrupamentoId)).thenReturn(Optional.of(agrupamento));
        return agrupamento;
    }

    private void salvarDevolvendoOArgumento() {
        when(subdivisaoRepository.save(any(Subdivisao.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void criar_emCampoMissionario_exigeSoNome() {
        agrupamentoExistente(Agrupamento.builder().tipo(CAMPO_MISSIONARIO).nome("Argentina").pais("Argentina").build());
        salvarDevolvendoOArgumento();

        Subdivisao local = subdivisaoService.criar(agrupamentoId, new SubdivisaoRequest(null, "  Alejandro Korn "));

        assertThat(local.getNome()).isEqualTo("Alejandro Korn");
        assertThat(local.getNumero()).isNull();
        assertThatThrownBy(() -> subdivisaoService.criar(agrupamentoId, new SubdivisaoRequest(1, "Alejandro Korn")))
                .isInstanceOf(AgrupamentoInvalidoException.class);
        assertThatThrownBy(() -> subdivisaoService.criar(agrupamentoId, new SubdivisaoRequest(null, null)))
                .isInstanceOf(AgrupamentoInvalidoException.class);
    }

    @Test
    void criar_emFilialESetor_exigeSoNumero() {
        agrupamentoExistente(Agrupamento.builder().tipo(SETOR).nome("Cabo").numero(1).build());
        salvarDevolvendoOArgumento();

        Subdivisao area = subdivisaoService.criar(agrupamentoId, new SubdivisaoRequest(10, null));

        assertThat(area.getNumero()).isEqualTo(10);
        assertThat(area.getNome()).isNull();
        assertThatThrownBy(() -> subdivisaoService.criar(agrupamentoId, new SubdivisaoRequest(null, null)))
                .isInstanceOf(AgrupamentoInvalidoException.class);
        assertThatThrownBy(() -> subdivisaoService.criar(agrupamentoId, new SubdivisaoRequest(10, "Nome")))
                .isInstanceOf(AgrupamentoInvalidoException.class);
    }

    @Test
    void criar_emAgrupamentoDoTipoArea_eRecusado() {
        agrupamentoExistente(Agrupamento.builder().tipo(AREA).numero(40).build());

        assertThatThrownBy(() -> subdivisaoService.criar(agrupamentoId, new SubdivisaoRequest(1, null)))
                .isInstanceOf(AgrupamentoInvalidoException.class);
        verify(subdivisaoRepository, never()).save(any());
    }

    @Test
    void criar_emAgrupamentoInativo_lancaExcecao() {
        agrupamentoExistente(Agrupamento.builder().tipo(FILIAL).nome("Petrolina").ativo(false).build());

        assertThatThrownBy(() -> subdivisaoService.criar(agrupamentoId, new SubdivisaoRequest(1, null)))
                .isInstanceOf(AgrupamentoInativoException.class);
    }

    @Test
    void criar_emAgrupamentoInexistente_lancaNaoEncontrado() {
        when(agrupamentoRepository.findById(agrupamentoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subdivisaoService.criar(agrupamentoId, new SubdivisaoRequest(1, null)))
                .isInstanceOf(AgrupamentoNaoEncontradoException.class);
    }

    @Test
    void criar_comNumeroRepetidoNoMesmoAgrupamento_lancaExcecao() {
        agrupamentoExistente(Agrupamento.builder().tipo(FILIAL).nome("Petrolina").build());
        when(subdivisaoRepository.existsByAgrupamentoIdAndNumero(agrupamentoId, 1)).thenReturn(true);

        assertThatThrownBy(() -> subdivisaoService.criar(agrupamentoId, new SubdivisaoRequest(1, null)))
                .isInstanceOf(AgrupamentoDuplicadoException.class);
        verify(subdivisaoRepository, never()).save(any());
    }

    @Test
    void criar_comNomeRepetidoNoMesmoCampo_lancaExcecao() {
        agrupamentoExistente(Agrupamento.builder().tipo(CAMPO_MISSIONARIO).nome("Argentina").pais("Argentina").build());
        when(subdivisaoRepository.existsByAgrupamentoIdAndNomeIgnoreCase(agrupamentoId, "Alejandro Korn")).thenReturn(true);

        assertThatThrownBy(() -> subdivisaoService.criar(agrupamentoId, new SubdivisaoRequest(null, "Alejandro Korn")))
                .isInstanceOf(AgrupamentoDuplicadoException.class);
    }

    @Test
    void buscar_deOutroAgrupamento_lancaNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(subdivisaoRepository.findByIdAndAgrupamentoId(id, agrupamentoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subdivisaoService.buscar(agrupamentoId, id)).isInstanceOf(SubdivisaoNaoEncontradaException.class);
    }

    @Test
    void listar_emAgrupamentoInexistente_lancaNaoEncontrado() {
        when(agrupamentoRepository.findById(agrupamentoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subdivisaoService.listar(agrupamentoId, null)).isInstanceOf(AgrupamentoNaoEncontradoException.class);
    }

    @Test
    void atualizar_ignoraOProprioRegistroNaChecagemDeDuplicidade() {
        UUID id = UUID.randomUUID();
        Agrupamento filial = Agrupamento.builder().id(agrupamentoId).tipo(FILIAL).nome("Petrolina").build();
        when(subdivisaoRepository.findByIdAndAgrupamentoId(id, agrupamentoId)).thenReturn(Optional.of(
                Subdivisao.builder().id(id).agrupamento(filial).numero(1).build()));
        when(subdivisaoRepository.existsByAgrupamentoIdAndNumeroAndIdNot(agrupamentoId, 2, id)).thenReturn(false);
        salvarDevolvendoOArgumento();

        assertThat(subdivisaoService.atualizar(agrupamentoId, id, new SubdivisaoRequest(2, null)).getNumero()).isEqualTo(2);
    }

    @Test
    void inativar_comCongregacoesAtivas_lancaExcecaoENaoAltera() {
        UUID id = UUID.randomUUID();
        Agrupamento filial = Agrupamento.builder().id(agrupamentoId).tipo(FILIAL).nome("Petrolina").build();
        Subdivisao area = Subdivisao.builder().id(id).agrupamento(filial).numero(1).build();
        when(subdivisaoRepository.findByIdAndAgrupamentoId(id, agrupamentoId)).thenReturn(Optional.of(area));
        when(congregacaoRepository.countBySubdivisaoIdAndAtivaTrue(id)).thenReturn(1L);

        assertThatThrownBy(() -> subdivisaoService.inativar(agrupamentoId, id))
                .isInstanceOf(AgrupamentoPossuiCongregacoesException.class);
        assertThat(area.isAtivo()).isTrue();
    }

    @Test
    void inativar_semCongregacoes_marcaComoInativa() {
        UUID id = UUID.randomUUID();
        Agrupamento campo = Agrupamento.builder().id(agrupamentoId).tipo(CAMPO_MISSIONARIO).nome("Argentina").pais("Argentina").build();
        Subdivisao local = Subdivisao.builder().id(id).agrupamento(campo).nome("Alejandro Korn").build();
        when(subdivisaoRepository.findByIdAndAgrupamentoId(id, agrupamentoId)).thenReturn(Optional.of(local));
        when(congregacaoRepository.countBySubdivisaoIdAndAtivaTrue(id)).thenReturn(0L);

        subdivisaoService.inativar(agrupamentoId, id);

        assertThat(local.isAtivo()).isFalse();
        verify(subdivisaoRepository).save(local);
    }

    @Test
    void inativarEReativar_dasubdivisaoImplicitaDeArea_saoRecusados() {
        UUID id = UUID.randomUUID();
        Agrupamento area = Agrupamento.builder().id(agrupamentoId).tipo(AREA).numero(40).build();
        when(subdivisaoRepository.findByIdAndAgrupamentoId(id, agrupamentoId)).thenReturn(Optional.of(
                Subdivisao.builder().id(id).agrupamento(area).build()));

        assertThatThrownBy(() -> subdivisaoService.inativar(agrupamentoId, id)).isInstanceOf(AgrupamentoInvalidoException.class);
        assertThatThrownBy(() -> subdivisaoService.reativar(agrupamentoId, id)).isInstanceOf(AgrupamentoInvalidoException.class);
    }
}
