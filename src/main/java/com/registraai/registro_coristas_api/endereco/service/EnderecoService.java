package com.registraai.registro_coristas_api.endereco.service;

import com.registraai.registro_coristas_api.endereco.dto.EnderecoRequest;
import com.registraai.registro_coristas_api.endereco.exception.EnderecoNaoEncontradoException;
import com.registraai.registro_coristas_api.endereco.model.Endereco;
import com.registraai.registro_coristas_api.endereco.repository.EnderecoRepository;
import com.registraai.registro_coristas_api.pessoa.repository.PessoaRepository;
import com.registraai.registro_coristas_api.pessoa.service.LimiteDeEdicaoDeContato;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EnderecoService {

    private final EnderecoRepository enderecoRepository;
    private final PessoaRepository pessoaRepository;
    private final LimiteDeEdicaoDeContato limiteDeEdicaoDeContato;

    @Transactional
    public Endereco criar(EnderecoRequest request) {
        Endereco endereco = new Endereco();
        preencher(endereco, request);
        return enderecoRepository.save(endereco);
    }

    @Transactional(readOnly = true)
    public Endereco buscarPorId(UUID id) {
        return enderecoRepository.findById(id)
                .orElseThrow(() -> new EnderecoNaoEncontradoException(id));
    }

    /**
     * Quando o endereço é de uma pessoa e quem altera é ela mesma, vale o limite de uma alteração a cada 10 dias, mas só
     * se algo realmente mudou: reenviar o mesmo endereço (o formulário do perfil faz isso) não conta.
     */
    @Transactional
    public Endereco atualizar(UUID id, EnderecoRequest request) {
        Endereco endereco = buscarPorId(id);
        Endereco novo = new Endereco();
        preencher(novo, request);
        if (mudou(endereco, novo)) {
            pessoaRepository.findByEnderecoId(id).ifPresent(limiteDeEdicaoDeContato::registrarEdicaoDeEndereco);
        }
        preencher(endereco, request);
        return enderecoRepository.save(endereco);
    }

    private boolean mudou(Endereco atual, Endereco novo) {
        return !Objects.equals(atual.getLogradouro(), novo.getLogradouro())
                || !Objects.equals(atual.getNumero(), novo.getNumero())
                || !Objects.equals(atual.getComplemento(), novo.getComplemento())
                || !Objects.equals(atual.getBairro(), novo.getBairro())
                || !Objects.equals(atual.getCidade(), novo.getCidade())
                || !Objects.equals(atual.getUf(), novo.getUf())
                || !Objects.equals(atual.getCep(), novo.getCep());
    }

    // Normaliza o que veio do front: espaços nas pontas, UF em maiúsculas, CEP só com dígitos,
    // e texto opcional em branco vira null (evita gravar "" no banco).
    private void preencher(Endereco endereco, EnderecoRequest request) {
        endereco.setLogradouro(request.logradouro().trim());
        endereco.setNumero(textoOpcional(request.numero()));
        endereco.setComplemento(textoOpcional(request.complemento()));
        endereco.setBairro(request.bairro().trim());
        endereco.setCidade(request.cidade().trim());
        endereco.setUf(request.uf().trim().toUpperCase(Locale.ROOT));
        endereco.setCep(cepSomenteDigitos(request.cep()));
    }

    private String textoOpcional(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.trim();
    }

    private String cepSomenteDigitos(String cep) {
        if (cep == null || cep.isBlank()) {
            return null;
        }
        return cep.replace("-", "");
    }
}
