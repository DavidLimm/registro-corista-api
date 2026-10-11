package com.registraai.registro_coristas_api.pessoa.service;

import com.registraai.registro_coristas_api.autenticacao.service.UsuarioLogado;
import com.registraai.registro_coristas_api.pessoa.exception.LimiteDeEdicaoDeContatoException;
import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.usuario.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LimiteDeEdicaoDeContatoTest {

    private static final Instant HOJE = Instant.parse("2026-10-10T15:00:00Z");

    @Mock
    private UsuarioLogado usuarioLogado;

    private Pessoa pessoa;

    @BeforeEach
    void logarComoAPropriaPessoaSemPermissaoDeEditarFichas() {
        pessoa = Pessoa.builder().id(UUID.randomUUID()).nome("Ana").build();
        Usuario usuario = new Usuario();
        usuario.setPessoa(pessoa);
        lenient().when(usuarioLogado.atual()).thenReturn(Optional.of(usuario));
        lenient().when(usuarioLogado.temPermissao("PESSOA_ATUALIZAR")).thenReturn(false);
    }

    private LimiteDeEdicaoDeContato limiteEm(Instant instante) {
        return new LimiteDeEdicaoDeContato(usuarioLogado, Clock.fixed(instante, ZoneId.of("America/Recife")));
    }

    @Test
    void primeiraEdicaoPassaEFicaRegistrada() {
        limiteEm(HOJE).registrarEdicaoDeTelefone(pessoa);

        assertThat(pessoa.getTelefoneEditadoEm()).isEqualTo(HOJE);
    }

    @Test
    void segundaEdicaoEmMenosDeDezDias_negaComAProximaDataPermitida() {
        limiteEm(HOJE).registrarEdicaoDeEndereco(pessoa);

        assertThatThrownBy(() -> limiteEm(HOJE.plus(Duration.ofDays(9))).registrarEdicaoDeEndereco(pessoa))
                .isInstanceOf(LimiteDeEdicaoDeContatoException.class)
                .satisfies(e -> assertThat(((LimiteDeEdicaoDeContatoException) e).getBody().getDetail())
                        .contains("seu endereço").contains("20/10/2026"));
        assertThat(pessoa.getEnderecoEditadoEm()).isEqualTo(HOJE);
    }

    @Test
    void depoisDeDezDiasPodeEditarDeNovo() {
        limiteEm(HOJE).registrarEdicaoDeTelefone(pessoa);
        Instant dezDiasDepois = HOJE.plus(Duration.ofDays(10));

        assertThatCode(() -> limiteEm(dezDiasDepois).registrarEdicaoDeTelefone(pessoa)).doesNotThrowAnyException();
        assertThat(pessoa.getTelefoneEditadoEm()).isEqualTo(dezDiasDepois);
    }

    @Test
    void telefoneEEnderecoTemContagensSeparadas() {
        limiteEm(HOJE).registrarEdicaoDeTelefone(pessoa);

        assertThatCode(() -> limiteEm(HOJE).registrarEdicaoDeEndereco(pessoa)).doesNotThrowAnyException();
    }

    @Test
    void quemTemPermissaoDeEditarFichas_naoELimitadoNemRegistra() {
        when(usuarioLogado.temPermissao("PESSOA_ATUALIZAR")).thenReturn(true);

        limiteEm(HOJE).registrarEdicaoDeTelefone(pessoa);
        limiteEm(HOJE).registrarEdicaoDeTelefone(pessoa);

        assertThat(pessoa.getTelefoneEditadoEm()).isNull();
    }

    @Test
    void editarAFichaDeOutraPessoa_naoELimitado() {
        Pessoa outra = Pessoa.builder().id(UUID.randomUUID()).nome("Outra").build();

        limiteEm(HOJE).registrarEdicaoDeEndereco(outra);
        limiteEm(HOJE).registrarEdicaoDeEndereco(outra);

        assertThat(outra.getEnderecoEditadoEm()).isNull();
    }

    @Test
    void semUsuarioLogado_naoLimita() {
        when(usuarioLogado.atual()).thenReturn(Optional.empty());

        limiteEm(HOJE).registrarEdicaoDeTelefone(pessoa);
        limiteEm(HOJE).registrarEdicaoDeTelefone(pessoa);

        assertThat(pessoa.getTelefoneEditadoEm()).isNull();
    }
}
