package com.registraai.registro_coristas_api.pessoa.service;

import com.registraai.registro_coristas_api.autenticacao.service.UsuarioLogado;
import com.registraai.registro_coristas_api.pessoa.repository.PessoaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Usado nas regras de {@code @PreAuthorize} ({@code @contatoDoProprio.ehMinhaPessoa(#pessoaId)}): além de quem tem a
 * permissão de editar/ler pessoas, cada um acessa o telefone e o endereço da <b>própria</b> pessoa.
 */
@Component("contatoDoProprio")
@RequiredArgsConstructor
public class ContatoDoProprio {

    private final UsuarioLogado usuarioLogado;
    private final PessoaRepository pessoaRepository;

    public boolean ehMinhaPessoa(UUID pessoaId) {
        return usuarioLogado.atual()
                .map(usuario -> usuario.getPessoa().getId().equals(pessoaId))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean ehMeuEndereco(UUID enderecoId) {
        return usuarioLogado.atual()
                .map(usuario -> pessoaRepository.existsByIdAndEnderecoId(usuario.getPessoa().getId(), enderecoId))
                .orElse(false);
    }
}
