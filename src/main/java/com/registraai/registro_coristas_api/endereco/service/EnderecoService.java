package com.registraai.registro_coristas_api.endereco.service;

import com.registraai.registro_coristas_api.endereco.dto.EnderecoRequest;
import com.registraai.registro_coristas_api.endereco.exception.EnderecoNaoEncontradoException;
import com.registraai.registro_coristas_api.endereco.model.Endereco;
import com.registraai.registro_coristas_api.endereco.repository.EnderecoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EnderecoService {

    private final EnderecoRepository enderecoRepository;

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

    @Transactional
    public Endereco atualizar(UUID id, EnderecoRequest request) {
        Endereco endereco = buscarPorId(id);
        preencher(endereco, request);
        return enderecoRepository.save(endereco);
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
