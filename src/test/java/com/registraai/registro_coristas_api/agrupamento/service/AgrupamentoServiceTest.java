package com.registraai.registro_coristas_api.agrupamento.service;

import com.registraai.registro_coristas_api.agrupamento.dto.AgrupamentoRequest;
import com.registraai.registro_coristas_api.agrupamento.exception.AgrupamentoDuplicadoException;
import com.registraai.registro_coristas_api.agrupamento.exception.AgrupamentoInvalidoException;
import com.registraai.registro_coristas_api.agrupamento.exception.AgrupamentoNaoEncontradoException;
import com.registraai.registro_coristas_api.agrupamento.exception.AgrupamentoPossuiCongregacoesException;
import com.registraai.registro_coristas_api.agrupamento.model.Agrupamento;
import com.registraai.registro_coristas_api.agrupamento.model.Subdivisao;
import com.registraai.registro_coristas_api.agrupamento.model.TipoAgrupamento;
import com.registraai.registro_coristas_api.agrupamento.repository.AgrupamentoRepository;
import com.registraai.registro_coristas_api.agrupamento.repository.SubdivisaoRepository;
import com.registraai.registro_coristas_api.congregacao.repository.CongregacaoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class AgrupamentoServiceTest {

    @Mock
    private AgrupamentoRepository agrupamentoRepository;

    @Mock
    private SubdivisaoRepository subdivisaoRepository;

    @Mock
    private CongregacaoRepository congregacaoRepository;

    @InjectMocks
    private AgrupamentoService agrupamentoService;

    private void salvarDevolvendoOArgumento() {
        when(agrupamentoRepository.save(any(Agrupamento.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ---------- criar: campos por tipo ----------

    @Test
    void criarArea_gravaSoONumeroECriaASubdivisaoImplicita() {
        salvarDevolvendoOArgumento();

        Agrupamento area = agrupamentoService.criar(new AgrupamentoRequest(AREA, null, 75, null));

        assertThat(area.getTipo()).isEqualTo(AREA);
        assertThat(area.getNumero()).isEqualTo(75);
        assertThat(area.getNome()).isNull();
        assertThat(area.isAtivo()).isTrue();
        ArgumentCaptor<Subdivisao> captor = ArgumentCaptor.forClass(Subdivisao.class);
        verify(subdivisaoRepository).save(captor.capture());
        assertThat(captor.getValue().getAgrupamento()).isSameAs(area);
        assertThat(captor.getValue().getNumero()).isNull();
        assertThat(captor.getValue().getNome()).isNull();
    }

    @Test
    void criarArea_recusaNomeEPaisENaoGrava() {
        assertThatThrownBy(() -> agrupamentoService.criar(new AgrupamentoRequest(AREA, "Área 75", 75, null)))
                .isInstanceOf(AgrupamentoInvalidoException.class);
        assertThatThrownBy(() -> agrupamentoService.criar(new AgrupamentoRequest(AREA, null, 75, "Brasil")))
                .isInstanceOf(AgrupamentoInvalidoException.class);
        verify(agrupamentoRepository, never()).save(any());
    }

    @Test
    void criarArea_semNumero_lancaExcecao() {
        assertThatThrownBy(() -> agrupamentoService.criar(new AgrupamentoRequest(AREA, null, null, null)))
                .isInstanceOf(AgrupamentoInvalidoException.class);
    }

    @Test
    void criarCampoMissionario_exigeNomeEPais_eAparaEspacos() {
        salvarDevolvendoOArgumento();

        Agrupamento campo = agrupamentoService.criar(new AgrupamentoRequest(CAMPO_MISSIONARIO, "  Argentina  ", null, " Argentina "));

        assertThat(campo.getNome()).isEqualTo("Argentina");
        assertThat(campo.getPais()).isEqualTo("Argentina");
        verify(subdivisaoRepository, never()).save(any());
        assertThatThrownBy(() -> agrupamentoService.criar(new AgrupamentoRequest(CAMPO_MISSIONARIO, "Argentina", null, "  ")))
                .isInstanceOf(AgrupamentoInvalidoException.class);
        assertThatThrownBy(() -> agrupamentoService.criar(new AgrupamentoRequest(CAMPO_MISSIONARIO, "Argentina", 3, "Argentina")))
                .isInstanceOf(AgrupamentoInvalidoException.class);
    }

    @Test
    void criarFilial_soNome() {
        salvarDevolvendoOArgumento();

        Agrupamento filial = agrupamentoService.criar(new AgrupamentoRequest(FILIAL, "Petrolina", null, null));

        assertThat(filial.getNome()).isEqualTo("Petrolina");
        assertThatThrownBy(() -> agrupamentoService.criar(new AgrupamentoRequest(FILIAL, "Petrolina", 1, null)))
                .isInstanceOf(AgrupamentoInvalidoException.class);
        assertThatThrownBy(() -> agrupamentoService.criar(new AgrupamentoRequest(FILIAL, null, null, null)))
                .isInstanceOf(AgrupamentoInvalidoException.class);
    }

    @Test
    void criarSetor_exigeNumeroENome() {
        salvarDevolvendoOArgumento();

        Agrupamento setor = agrupamentoService.criar(new AgrupamentoRequest(SETOR, "Cabo de Santo Agostinho", 1, null));

        assertThat(setor.getNumero()).isEqualTo(1);
        assertThat(setor.getNome()).isEqualTo("Cabo de Santo Agostinho");
        assertThatThrownBy(() -> agrupamentoService.criar(new AgrupamentoRequest(SETOR, null, 2, null)))
                .isInstanceOf(AgrupamentoInvalidoException.class);
        assertThatThrownBy(() -> agrupamentoService.criar(new AgrupamentoRequest(SETOR, "Setor", null, null)))
                .isInstanceOf(AgrupamentoInvalidoException.class);
    }

    // ---------- duplicidade ----------

    @Test
    void criarArea_comNumeroJaExistente_lancaExcecaoENaoGrava() {
        when(agrupamentoRepository.existsByTipoAndNumero(AREA, 40)).thenReturn(true);

        assertThatThrownBy(() -> agrupamentoService.criar(new AgrupamentoRequest(AREA, null, 40, null)))
                .isInstanceOf(AgrupamentoDuplicadoException.class);
        verify(agrupamentoRepository, never()).save(any());
    }

    @Test
    void criarSetor_comNumeroJaExistenteEntreSetores_lancaExcecao() {
        when(agrupamentoRepository.existsByTipoAndNumero(SETOR, 1)).thenReturn(true);

        assertThatThrownBy(() -> agrupamentoService.criar(new AgrupamentoRequest(SETOR, "Outro", 1, null)))
                .isInstanceOf(AgrupamentoDuplicadoException.class);
    }

    @Test
    void criarFilial_comNomeJaExistente_lancaExcecao() {
        when(agrupamentoRepository.existsByTipoAndNomeIgnoreCase(FILIAL, "Petrolina")).thenReturn(true);

        assertThatThrownBy(() -> agrupamentoService.criar(new AgrupamentoRequest(FILIAL, "Petrolina", null, null)))
                .isInstanceOf(AgrupamentoDuplicadoException.class);
    }

    // ---------- buscar / atualizar ----------

    @Test
    void buscarPorId_inexistente_lancaNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(agrupamentoRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agrupamentoService.buscarPorId(id)).isInstanceOf(AgrupamentoNaoEncontradoException.class);
    }

    @Test
    void atualizar_naoPermiteTrocarOTipo() {
        UUID id = UUID.randomUUID();
        when(agrupamentoRepository.findById(id)).thenReturn(Optional.of(
                Agrupamento.builder().id(id).tipo(FILIAL).nome("Petrolina").build()));

        assertThatThrownBy(() -> agrupamentoService.atualizar(id, new AgrupamentoRequest(SETOR, "Petrolina", 1, null)))
                .isInstanceOf(AgrupamentoInvalidoException.class);
        verify(agrupamentoRepository, never()).save(any());
    }

    @Test
    void atualizar_ignoraOProprioRegistroNaChecagemDeDuplicidade() {
        UUID id = UUID.randomUUID();
        when(agrupamentoRepository.findById(id)).thenReturn(Optional.of(
                Agrupamento.builder().id(id).tipo(SETOR).nome("Cabo").numero(1).build()));
        when(agrupamentoRepository.existsByTipoAndNumeroAndIdNot(SETOR, 1, id)).thenReturn(false);
        salvarDevolvendoOArgumento();

        Agrupamento atualizado = agrupamentoService.atualizar(id, new AgrupamentoRequest(SETOR, "Cabo de Santo Agostinho", 1, null));

        assertThat(atualizado.getNome()).isEqualTo("Cabo de Santo Agostinho");
    }

    @Test
    void atualizar_comNumeroDeOutroSetor_lancaExcecao() {
        UUID id = UUID.randomUUID();
        when(agrupamentoRepository.findById(id)).thenReturn(Optional.of(
                Agrupamento.builder().id(id).tipo(SETOR).nome("Cabo").numero(1).build()));
        when(agrupamentoRepository.existsByTipoAndNumeroAndIdNot(SETOR, 2, id)).thenReturn(true);

        assertThatThrownBy(() -> agrupamentoService.atualizar(id, new AgrupamentoRequest(SETOR, "Cabo", 2, null)))
                .isInstanceOf(AgrupamentoDuplicadoException.class);
    }

    // ---------- inativar / reativar ----------

    @Test
    void inativar_semCongregacoesAtivas_marcaComoInativo() {
        UUID id = UUID.randomUUID();
        Agrupamento area = Agrupamento.builder().id(id).tipo(AREA).numero(75).build();
        when(agrupamentoRepository.findById(id)).thenReturn(Optional.of(area));
        when(congregacaoRepository.contarAtivasPorAgrupamento(id)).thenReturn(0L);

        agrupamentoService.inativar(id);

        assertThat(area.isAtivo()).isFalse();
        verify(agrupamentoRepository).save(area);
    }

    @Test
    void inativar_comCongregacoesAtivas_lancaExcecaoENaoAltera() {
        UUID id = UUID.randomUUID();
        Agrupamento area = Agrupamento.builder().id(id).tipo(AREA).numero(40).build();
        when(agrupamentoRepository.findById(id)).thenReturn(Optional.of(area));
        when(congregacaoRepository.contarAtivasPorAgrupamento(id)).thenReturn(13L);

        assertThatThrownBy(() -> agrupamentoService.inativar(id))
                .isInstanceOf(AgrupamentoPossuiCongregacoesException.class)
                .hasMessageContaining("13 congregações");
        assertThat(area.isAtivo()).isTrue();
        verify(agrupamentoRepository, never()).save(any());
    }

    @Test
    void reativar_voltaAFicarAtivo() {
        UUID id = UUID.randomUUID();
        Agrupamento area = Agrupamento.builder().id(id).tipo(AREA).numero(75).ativo(false).build();
        when(agrupamentoRepository.findById(id)).thenReturn(Optional.of(area));
        salvarDevolvendoOArgumento();

        assertThat(agrupamentoService.reativar(id).isAtivo()).isTrue();
    }

    @Test
    void descrever_formataCadaTipo() {
        assertThat(AgrupamentoService.descrever(Agrupamento.builder().tipo(AREA).numero(40).build())).isEqualTo("a Área 40");
        assertThat(AgrupamentoService.descrever(Agrupamento.builder().tipo(SETOR).numero(1).nome("Cabo").build()))
                .isEqualTo("o Setor 1 (Cabo)");
        assertThat(AgrupamentoService.descrever(Agrupamento.builder().tipo(FILIAL).nome("Petrolina").build()))
                .isEqualTo("a Filial Petrolina");
        assertThat(AgrupamentoService.descrever(Agrupamento.builder().tipo(TipoAgrupamento.CAMPO_MISSIONARIO).nome("Argentina").build()))
                .isEqualTo("o Campo Missionário Argentina");
    }
}
