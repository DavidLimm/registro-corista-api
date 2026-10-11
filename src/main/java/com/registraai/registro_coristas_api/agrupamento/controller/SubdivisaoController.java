package com.registraai.registro_coristas_api.agrupamento.controller;

import com.registraai.registro_coristas_api.agrupamento.dto.SubdivisaoRequest;
import com.registraai.registro_coristas_api.agrupamento.dto.SubdivisaoResponse;
import com.registraai.registro_coristas_api.agrupamento.model.Subdivisao;
import com.registraai.registro_coristas_api.agrupamento.service.SubdivisaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/agrupamentos/{agrupamentoId}/subdivisoes")
@RequiredArgsConstructor
public class SubdivisaoController {

    private final SubdivisaoService subdivisaoService;

    @PreAuthorize("hasAuthority('AGRUPAMENTO_GERENCIAR')")
    @PostMapping
    public ResponseEntity<SubdivisaoResponse> criar(@PathVariable UUID agrupamentoId,
                                                    @Valid @RequestBody SubdivisaoRequest request) {
        Subdivisao subdivisao = subdivisaoService.criar(agrupamentoId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(subdivisao.getId())
                .toUri();
        return ResponseEntity.created(location).body(SubdivisaoResponse.de(subdivisao));
    }

    /** Público (só leitura), como a listagem de agrupamentos. */
    @GetMapping
    public List<SubdivisaoResponse> listar(@PathVariable UUID agrupamentoId,
                                           @RequestParam(required = false) Boolean ativo) {
        return subdivisaoService.listar(agrupamentoId, ativo).stream().map(SubdivisaoResponse::de).toList();
    }

    @GetMapping("/{id}")
    public SubdivisaoResponse buscar(@PathVariable UUID agrupamentoId, @PathVariable UUID id) {
        return SubdivisaoResponse.de(subdivisaoService.buscar(agrupamentoId, id));
    }

    @PreAuthorize("hasAuthority('AGRUPAMENTO_GERENCIAR')")
    @PutMapping("/{id}")
    public SubdivisaoResponse atualizar(@PathVariable UUID agrupamentoId, @PathVariable UUID id,
                                        @Valid @RequestBody SubdivisaoRequest request) {
        return SubdivisaoResponse.de(subdivisaoService.atualizar(agrupamentoId, id, request));
    }

    @PreAuthorize("hasAuthority('AGRUPAMENTO_GERENCIAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativar(@PathVariable UUID agrupamentoId, @PathVariable UUID id) {
        subdivisaoService.inativar(agrupamentoId, id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('AGRUPAMENTO_GERENCIAR')")
    @PatchMapping("/{id}/reativar")
    public SubdivisaoResponse reativar(@PathVariable UUID agrupamentoId, @PathVariable UUID id) {
        return SubdivisaoResponse.de(subdivisaoService.reativar(agrupamentoId, id));
    }
}
