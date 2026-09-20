package com.registraai.registro_coristas_api.telefone.service;

import com.registraai.registro_coristas_api.pessoa.exception.PessoaNaoEncontradaException;
import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.pessoa.service.PessoaService;
import com.registraai.registro_coristas_api.telefone.dto.TelefoneRequest;
import com.registraai.registro_coristas_api.telefone.exception.TelefoneDuplicadoException;
import com.registraai.registro_coristas_api.telefone.exception.TelefoneNaoEncontradoException;
import com.registraai.registro_coristas_api.telefone.model.Telefone;
import com.registraai.registro_coristas_api.telefone.repository.TelefoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelefoneServiceTest {

    @Mock
    private TelefoneRepository telefoneRepository;

    @Mock
    private PessoaService pessoaService;

    private TelefoneService telefoneService;

    private final UUID pessoaId = UUID.randomUUID();
    private Pessoa pessoa;

    @BeforeEach
    void preparar() {
        telefoneService = new TelefoneService(telefoneRepository, pessoaService);
        pessoa = Pessoa.builder().id(pessoaId).nome("Maria da Silva").build();
    }

    private Telefone telefone(String numero, boolean principal) {
        return Telefone.builder().id(UUID.randomUUID()).pessoa(pessoa).numero(numero).principal(principal).build();
    }

    private void pessoaExiste() {
        when(pessoaService.buscarPorId(pessoaId)).thenReturn(pessoa);
    }

    private void gravacaoDevolveOQueRecebeu() {
        when(telefoneRepository.save(any(Telefone.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    // ---------- criar ----------

    @Test
    void criar_primeiroTelefoneDaPessoa_viraPrincipalEGravaSoOsDigitos() {
        pessoaExiste();
        gravacaoDevolveOQueRecebeu();
        when(telefoneRepository.existsByPessoaIdAndNumero(pessoaId, "81999990000")).thenReturn(false);
        when(telefoneRepository.existsByPessoaId(pessoaId)).thenReturn(false);
        when(telefoneRepository.findByPessoaIdAndPrincipalTrue(pessoaId)).thenReturn(Optional.empty());

        Telefone criado = telefoneService.criar(pessoaId, new TelefoneRequest("(81) 99999-0000", true, false));

        assertThat(criado.getNumero()).isEqualTo("81999990000");
        assertThat(criado.isWhatsapp()).isTrue();
        assertThat(criado.isPrincipal()).isTrue();
        assertThat(criado.getPessoa()).isSameAs(pessoa);
    }

    @Test
    void criar_segundoTelefoneSemPedirPrincipal_naoEPrincipalENaoMexeNoAtual() {
        pessoaExiste();
        gravacaoDevolveOQueRecebeu();
        when(telefoneRepository.existsByPessoaIdAndNumero(pessoaId, "8133334444")).thenReturn(false);
        when(telefoneRepository.existsByPessoaId(pessoaId)).thenReturn(true);

        Telefone criado = telefoneService.criar(pessoaId, new TelefoneRequest("8133334444", false, false));

        assertThat(criado.isPrincipal()).isFalse();
        verify(telefoneRepository, never()).findByPessoaIdAndPrincipalTrue(any());
        verify(telefoneRepository, never()).saveAndFlush(any());
    }

    @Test
    void criar_comFlagsAusentes_tratacomoFalse() {
        pessoaExiste();
        gravacaoDevolveOQueRecebeu();
        when(telefoneRepository.existsByPessoaIdAndNumero(pessoaId, "8133334444")).thenReturn(false);
        when(telefoneRepository.existsByPessoaId(pessoaId)).thenReturn(true);

        Telefone criado = telefoneService.criar(pessoaId, new TelefoneRequest("8133334444", null, null));

        assertThat(criado.isWhatsapp()).isFalse();
        assertThat(criado.isPrincipal()).isFalse();
    }

    @Test
    void criar_pedindoPrincipal_rebaixaOAtualAntesDeGravarONovo() {
        pessoaExiste();
        gravacaoDevolveOQueRecebeu();
        Telefone atual = telefone("81988887777", true);
        when(telefoneRepository.existsByPessoaIdAndNumero(pessoaId, "81999990000")).thenReturn(false);
        when(telefoneRepository.findByPessoaIdAndPrincipalTrue(pessoaId)).thenReturn(Optional.of(atual));

        Telefone criado = telefoneService.criar(pessoaId, new TelefoneRequest("81999990000", false, true));

        assertThat(criado.isPrincipal()).isTrue();
        assertThat(atual.isPrincipal()).isFalse();
        // o rebaixamento precisa ir ao banco antes do INSERT: o índice único parcial não aceita dois principais
        InOrder ordem = inOrder(telefoneRepository);
        ordem.verify(telefoneRepository).saveAndFlush(atual);
        ordem.verify(telefoneRepository).save(criado);
    }

    @Test
    void criar_numeroJaCadastradoParaAPessoa_lancaESemGravar() {
        pessoaExiste();
        when(telefoneRepository.existsByPessoaIdAndNumero(pessoaId, "81999990000")).thenReturn(true);

        assertThatThrownBy(() -> telefoneService.criar(pessoaId, new TelefoneRequest("(81) 99999-0000", false, false)))
                .isInstanceOf(TelefoneDuplicadoException.class);

        verify(telefoneRepository, never()).save(any());
        verify(telefoneRepository, never()).saveAndFlush(any());
    }

    @Test
    void criar_pessoaInexistente_lancaSemTocarNoRepositorio() {
        when(pessoaService.buscarPorId(pessoaId)).thenThrow(new PessoaNaoEncontradaException(pessoaId));

        assertThatThrownBy(() -> telefoneService.criar(pessoaId, new TelefoneRequest("81999990000", false, false)))
                .isInstanceOf(PessoaNaoEncontradaException.class);

        verifyNoInteractions(telefoneRepository);
    }

    // ---------- listar / buscar ----------

    @Test
    void listar_devolveOsTelefonesDaPessoa() {
        pessoaExiste();
        List<Telefone> telefones = List.of(telefone("81999990000", true), telefone("8133334444", false));
        when(telefoneRepository.findByPessoaIdOrderByPrincipalDescCriadoEmAsc(pessoaId)).thenReturn(telefones);

        assertThat(telefoneService.listar(pessoaId)).isEqualTo(telefones);
    }

    @Test
    void listar_pessoaInexistente_lancaEmVezDeDevolverListaVazia() {
        when(pessoaService.buscarPorId(pessoaId)).thenThrow(new PessoaNaoEncontradaException(pessoaId));

        assertThatThrownBy(() -> telefoneService.listar(pessoaId)).isInstanceOf(PessoaNaoEncontradaException.class);

        verifyNoInteractions(telefoneRepository);
    }

    @Test
    void buscarPorId_telefoneDeOutraPessoa_lancaNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(telefoneRepository.findByIdAndPessoaId(id, pessoaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> telefoneService.buscarPorId(pessoaId, id))
                .isInstanceOf(TelefoneNaoEncontradoException.class);
    }

    // ---------- atualizar ----------

    @Test
    void atualizar_alteraNumeroEWhatsappSemMexerNoPrincipal() {
        Telefone existente = telefone("81999990000", true);
        gravacaoDevolveOQueRecebeu();
        when(telefoneRepository.findByIdAndPessoaId(existente.getId(), pessoaId)).thenReturn(Optional.of(existente));
        when(telefoneRepository.existsByPessoaIdAndNumeroAndIdNot(pessoaId, "8133334444", existente.getId()))
                .thenReturn(false);

        Telefone atualizado = telefoneService.atualizar(pessoaId, existente.getId(),
                new TelefoneRequest("(81) 3333-4444", true, false));

        assertThat(atualizado.getNumero()).isEqualTo("8133334444");
        assertThat(atualizado.isWhatsapp()).isTrue();
        assertThat(atualizado.isPrincipal()).isTrue();
        verify(telefoneRepository, never()).saveAndFlush(any());
    }

    @Test
    void atualizar_pedindoPrincipal_rebaixaOAtualETornaEsteOPrincipal() {
        Telefone atual = telefone("81988887777", true);
        Telefone existente = telefone("81999990000", false);
        gravacaoDevolveOQueRecebeu();
        when(telefoneRepository.findByIdAndPessoaId(existente.getId(), pessoaId)).thenReturn(Optional.of(existente));
        when(telefoneRepository.existsByPessoaIdAndNumeroAndIdNot(pessoaId, "81999990000", existente.getId()))
                .thenReturn(false);
        when(telefoneRepository.findByPessoaIdAndPrincipalTrue(pessoaId)).thenReturn(Optional.of(atual));

        Telefone atualizado = telefoneService.atualizar(pessoaId, existente.getId(),
                new TelefoneRequest("81999990000", false, true));

        assertThat(atualizado.isPrincipal()).isTrue();
        assertThat(atual.isPrincipal()).isFalse();
        InOrder ordem = inOrder(telefoneRepository);
        ordem.verify(telefoneRepository).saveAndFlush(atual);
        ordem.verify(telefoneRepository).save(existente);
    }

    @Test
    void atualizar_principalFalseNoTelefonePrincipal_mantemOPrincipal() {
        Telefone existente = telefone("81999990000", true);
        gravacaoDevolveOQueRecebeu();
        when(telefoneRepository.findByIdAndPessoaId(existente.getId(), pessoaId)).thenReturn(Optional.of(existente));
        when(telefoneRepository.existsByPessoaIdAndNumeroAndIdNot(pessoaId, "81999990000", existente.getId()))
                .thenReturn(false);

        Telefone atualizado = telefoneService.atualizar(pessoaId, existente.getId(),
                new TelefoneRequest("81999990000", false, false));

        // rebaixar sem indicar outro deixaria a pessoa sem principal
        assertThat(atualizado.isPrincipal()).isTrue();
    }

    @Test
    void atualizar_numeroDeOutroTelefoneDaMesmaPessoa_lancaDuplicadoESemGravar() {
        Telefone existente = telefone("81999990000", true);
        when(telefoneRepository.findByIdAndPessoaId(existente.getId(), pessoaId)).thenReturn(Optional.of(existente));
        when(telefoneRepository.existsByPessoaIdAndNumeroAndIdNot(pessoaId, "8133334444", existente.getId()))
                .thenReturn(true);

        assertThatThrownBy(() -> telefoneService.atualizar(pessoaId, existente.getId(),
                new TelefoneRequest("8133334444", false, false)))
                .isInstanceOf(TelefoneDuplicadoException.class);

        verify(telefoneRepository, never()).save(any());
        assertThat(existente.getNumero()).isEqualTo("81999990000");
    }

    @Test
    void atualizar_telefoneDeOutraPessoa_lancaNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(telefoneRepository.findByIdAndPessoaId(id, pessoaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> telefoneService.atualizar(pessoaId, id,
                new TelefoneRequest("81999990000", false, false)))
                .isInstanceOf(TelefoneNaoEncontradoException.class);

        verify(telefoneRepository, never()).save(any());
    }

    // ---------- remover ----------

    @Test
    void remover_telefoneQueNaoEPrincipal_naoPromoveNinguem() {
        Telefone existente = telefone("8133334444", false);
        when(telefoneRepository.findByIdAndPessoaId(existente.getId(), pessoaId)).thenReturn(Optional.of(existente));

        telefoneService.remover(pessoaId, existente.getId());

        verify(telefoneRepository).delete(existente);
        verify(telefoneRepository, never()).findByPessoaIdOrderByPrincipalDescCriadoEmAsc(any());
        verify(telefoneRepository, never()).save(any());
    }

    @Test
    void remover_telefonePrincipal_promoveOMaisAntigoDosRestantes() {
        Telefone principal = telefone("81999990000", true);
        Telefone maisAntigo = telefone("8133334444", false);
        Telefone maisNovo = telefone("81988887777", false);
        when(telefoneRepository.findByIdAndPessoaId(principal.getId(), pessoaId)).thenReturn(Optional.of(principal));
        when(telefoneRepository.findByPessoaIdOrderByPrincipalDescCriadoEmAsc(pessoaId))
                .thenReturn(List.of(maisAntigo, maisNovo));

        telefoneService.remover(pessoaId, principal.getId());

        assertThat(maisAntigo.isPrincipal()).isTrue();
        assertThat(maisNovo.isPrincipal()).isFalse();
        InOrder ordem = inOrder(telefoneRepository);
        ordem.verify(telefoneRepository).delete(principal);
        ordem.verify(telefoneRepository).flush();
        ordem.verify(telefoneRepository).save(maisAntigo);
    }

    @Test
    void remover_unicoTelefonePrincipal_naoHaQuemPromover() {
        Telefone principal = telefone("81999990000", true);
        when(telefoneRepository.findByIdAndPessoaId(principal.getId(), pessoaId)).thenReturn(Optional.of(principal));
        when(telefoneRepository.findByPessoaIdOrderByPrincipalDescCriadoEmAsc(pessoaId)).thenReturn(List.of());

        telefoneService.remover(pessoaId, principal.getId());

        verify(telefoneRepository).delete(principal);
        verify(telefoneRepository, never()).save(any());
    }

    @Test
    void remover_telefoneDeOutraPessoa_lancaNaoEncontradoSemRemover() {
        UUID id = UUID.randomUUID();
        when(telefoneRepository.findByIdAndPessoaId(id, pessoaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> telefoneService.remover(pessoaId, id))
                .isInstanceOf(TelefoneNaoEncontradoException.class);

        verify(telefoneRepository, never()).delete(any());
    }
}
