package com.registraai.registro_coristas_api.telefone.controller;

import com.registraai.registro_coristas_api.telefone.dto.TelefoneRequest;
import com.registraai.registro_coristas_api.telefone.dto.TelefoneResponse;
import com.registraai.registro_coristas_api.telefone.model.Telefone;
import com.registraai.registro_coristas_api.telefone.service.TelefoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Telefones aninhados na pessoa: um telefone só existe (e só é acessível) a partir da pessoa dona dele. */
@RestController
@RequestMapping("/v1/api/pessoas/{pessoaId}/telefones")
@RequiredArgsConstructor
public class TelefoneController {

    private final TelefoneService telefoneService;

    @PostMapping
    public ResponseEntity<TelefoneResponse> criar(@PathVariable UUID pessoaId,
                                                  @Valid @RequestBody TelefoneRequest request) {
        Telefone telefone = telefoneService.criar(pessoaId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(telefone.getId())
                .toUri();
        return ResponseEntity.created(location).body(TelefoneResponse.de(telefone));
    }

    /** Sem paginação (poucos por pessoa). O principal vem primeiro. */
    @GetMapping
    public List<TelefoneResponse> listar(@PathVariable UUID pessoaId) {
        return telefoneService.listar(pessoaId).stream().map(TelefoneResponse::de).toList();
    }

    @GetMapping("/{id}")
    public TelefoneResponse buscarPorId(@PathVariable UUID pessoaId, @PathVariable UUID id) {
        return TelefoneResponse.de(telefoneService.buscarPorId(pessoaId, id));
    }

    @PutMapping("/{id}")
    public TelefoneResponse atualizar(@PathVariable UUID pessoaId, @PathVariable UUID id,
                                      @Valid @RequestBody TelefoneRequest request) {
        return TelefoneResponse.de(telefoneService.atualizar(pessoaId, id, request));
    }

    /** Remove o registro (telefone não tem status). Se era o principal, o mais antigo restante assume. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable UUID pessoaId, @PathVariable UUID id) {
        telefoneService.remover(pessoaId, id);
        return ResponseEntity.noContent().build();
    }
}
