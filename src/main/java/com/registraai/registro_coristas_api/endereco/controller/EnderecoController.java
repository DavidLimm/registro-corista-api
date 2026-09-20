package com.registraai.registro_coristas_api.endereco.controller;

import com.registraai.registro_coristas_api.endereco.dto.EnderecoRequest;
import com.registraai.registro_coristas_api.endereco.dto.EnderecoResponse;
import com.registraai.registro_coristas_api.endereco.model.Endereco;
import com.registraai.registro_coristas_api.endereco.service.EnderecoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/enderecos")
@RequiredArgsConstructor
public class EnderecoController {

    private final EnderecoService enderecoService;

    @PostMapping
    public ResponseEntity<EnderecoResponse> criar(@Valid @RequestBody EnderecoRequest request) {
        Endereco endereco = enderecoService.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(endereco.getId())
                .toUri();
        return ResponseEntity.created(location).body(EnderecoResponse.de(endereco));
    }

    @GetMapping("/{id}")
    public EnderecoResponse buscarPorId(@PathVariable UUID id) {
        return EnderecoResponse.de(enderecoService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public EnderecoResponse atualizar(@PathVariable UUID id, @Valid @RequestBody EnderecoRequest request) {
        return EnderecoResponse.de(enderecoService.atualizar(id, request));
    }
}
