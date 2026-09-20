package com.registraai.registro_coristas_api.endereco.service;

import com.registraai.registro_coristas_api.endereco.dto.EnderecoRequest;
import com.registraai.registro_coristas_api.endereco.exception.EnderecoNaoEncontradoException;
import com.registraai.registro_coristas_api.endereco.model.Endereco;
import com.registraai.registro_coristas_api.endereco.repository.EnderecoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnderecoServiceTest {

    @Mock
    private EnderecoRepository enderecoRepository;

    @InjectMocks
    private EnderecoService enderecoService;

    @Test
    void criar_normalizaCamposEGravaSomenteDigitosDoCep() {
        when(enderecoRepository.save(any(Endereco.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
        var request = new EnderecoRequest(
                "  Rua da Aurora ", " 123 ", "  Apto 4  ", " Boa Vista ", " Recife ", "pe", "50050-000");

        Endereco endereco = enderecoService.criar(request);

        assertThat(endereco.getLogradouro()).isEqualTo("Rua da Aurora");
        assertThat(endereco.getNumero()).isEqualTo("123");
        assertThat(endereco.getComplemento()).isEqualTo("Apto 4");
        assertThat(endereco.getBairro()).isEqualTo("Boa Vista");
        assertThat(endereco.getCidade()).isEqualTo("Recife");
        assertThat(endereco.getUf()).isEqualTo("PE");
        assertThat(endereco.getCep()).isEqualTo("50050000");
    }

    @Test
    void criar_textoOpcionalEmBrancoOuCepAusenteViraNull() {
        when(enderecoRepository.save(any(Endereco.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
        var request = new EnderecoRequest("Sítio Bom Jesus", "   ", "", "Zona Rural", "Vitória de Santo Antão", "PE", null);

        Endereco endereco = enderecoService.criar(request);

        assertThat(endereco.getNumero()).isNull();
        assertThat(endereco.getComplemento()).isNull();
        assertThat(endereco.getCep()).isNull();
    }

    @Test
    void criar_aceitaCepJaSemHifen() {
        when(enderecoRepository.save(any(Endereco.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
        var request = new EnderecoRequest("Rua A", "S/N", null, "Centro", "Olinda", "PE", "53020000");

        Endereco endereco = enderecoService.criar(request);

        assertThat(endereco.getCep()).isEqualTo("53020000");
    }

    @Test
    void buscarPorId_retornaEnderecoExistente() {
        UUID id = UUID.randomUUID();
        Endereco existente = Endereco.builder().id(id).logradouro("Rua A").build();
        when(enderecoRepository.findById(id)).thenReturn(Optional.of(existente));

        assertThat(enderecoService.buscarPorId(id)).isSameAs(existente);
    }

    @Test
    void buscarPorId_lancaExcecaoQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(enderecoRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> enderecoService.buscarPorId(id))
                .isInstanceOf(EnderecoNaoEncontradoException.class);
    }

    @Test
    void atualizar_substituiTodosOsCamposNormalizados() {
        UUID id = UUID.randomUUID();
        Endereco existente = Endereco.builder()
                .id(id).logradouro("Rua Antiga").numero("1").complemento("Casa").bairro("Velho")
                .cidade("Olinda").uf("PE").cep("53020000").build();
        when(enderecoRepository.findById(id)).thenReturn(Optional.of(existente));
        when(enderecoRepository.save(any(Endereco.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
        var request = new EnderecoRequest("Rua Nova", null, null, "Novo", "Recife", "pe", "50050-000");

        Endereco atualizado = enderecoService.atualizar(id, request);

        assertThat(atualizado.getId()).isEqualTo(id);
        assertThat(atualizado.getLogradouro()).isEqualTo("Rua Nova");
        assertThat(atualizado.getNumero()).isNull();
        assertThat(atualizado.getComplemento()).isNull();
        assertThat(atualizado.getBairro()).isEqualTo("Novo");
        assertThat(atualizado.getCidade()).isEqualTo("Recife");
        assertThat(atualizado.getUf()).isEqualTo("PE");
        assertThat(atualizado.getCep()).isEqualTo("50050000");
    }

    @Test
    void atualizar_lancaExcecaoENaoGravaQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(enderecoRepository.findById(id)).thenReturn(Optional.empty());
        var request = new EnderecoRequest("Rua", null, null, "Bairro", "Cidade", "PE", null);

        assertThatThrownBy(() -> enderecoService.atualizar(id, request))
                .isInstanceOf(EnderecoNaoEncontradoException.class);
        verify(enderecoRepository, never()).save(any());
    }
}
