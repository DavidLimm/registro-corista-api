package com.registraai.registro_coristas_api.pessoa.service;

import com.registraai.registro_coristas_api.autenticacao.service.UsuarioLogado;
import com.registraai.registro_coristas_api.pessoa.exception.LimiteDeEdicaoDeContatoException;
import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Quem altera o próprio telefone ou endereço só pode fazê-lo uma vez a cada {@value #DIAS_ENTRE_EDICOES} dias (o telefone
 * e o endereço têm contagens separadas). Não limita quem edita a ficha de outra pessoa (liderança) nem quem tem
 * {@code PESSOA_ATUALIZAR}. Chamar <b>antes</b> de gravar a mudança, dentro da transação: o registro da data vai junto.
 */
@Service
@RequiredArgsConstructor
public class LimiteDeEdicaoDeContato {

    static final int DIAS_ENTRE_EDICOES = 10;
    private static final Duration INTERVALO = Duration.ofDays(DIAS_ENTRE_EDICOES);

    private final UsuarioLogado usuarioLogado;
    private final Clock clock;

    public void registrarEdicaoDeTelefone(Pessoa pessoa) {
        if (!limitaEstaEdicao(pessoa)) {
            return;
        }
        Instant agora = Instant.now(clock);
        exigirIntervalo(pessoa.getTelefoneEditadoEm(), agora, "seu telefone");
        pessoa.setTelefoneEditadoEm(agora);
    }

    public void registrarEdicaoDeEndereco(Pessoa pessoa) {
        if (!limitaEstaEdicao(pessoa)) {
            return;
        }
        Instant agora = Instant.now(clock);
        exigirIntervalo(pessoa.getEnderecoEditadoEm(), agora, "seu endereço");
        pessoa.setEnderecoEditadoEm(agora);
    }

    // só vale para a própria pessoa editando a si mesma, sem a permissão de editar fichas (liderança)
    private boolean limitaEstaEdicao(Pessoa pessoa) {
        return usuarioLogado.atual()
                .map(usuario -> usuario.getPessoa().getId().equals(pessoa.getId())
                        && !usuarioLogado.temPermissao("PESSOA_ATUALIZAR"))
                .orElse(false);
    }

    private void exigirIntervalo(Instant ultimaEdicao, Instant agora, String oQue) {
        if (ultimaEdicao != null && agora.isBefore(ultimaEdicao.plus(INTERVALO))) {
            throw new LimiteDeEdicaoDeContatoException(oQue, ultimaEdicao.plus(INTERVALO));
        }
    }
}
