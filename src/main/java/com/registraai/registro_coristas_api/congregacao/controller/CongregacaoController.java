package com.registraai.registro_coristas_api.congregacao.controller;

import com.registraai.registro_coristas_api.congregacao.dto.CongregacaoRequest;
import com.registraai.registro_coristas_api.congregacao.dto.CongregacaoResponse;
import com.registraai.registro_coristas_api.congregacao.model.Congregacao;
import com.registraai.registro_coristas_api.congregacao.service.CongregacaoService;
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
@RequestMapping("/v1/api/congregacoes")
@RequiredArgsConstructor
public class CongregacaoController {

    private final CongregacaoService congregacaoService;

    @PreAuthorize("hasAuthority('CONGREGACAO_GERENCIAR')")
    @PostMapping
    public ResponseEntity<CongregacaoResponse> criar(@Valid @RequestBody CongregacaoRequest request) {
        Congregacao congregacao = congregacaoService.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(congregacao.getId())
                .toUri();
        return ResponseEntity.created(location).body(CongregacaoResponse.de(congregacao));
    }

    @GetMapping
    public List<CongregacaoResponse> listar(@RequestParam(required = false) UUID agrupamentoId,
                                            @RequestParam(required = false) UUID subdivisaoId,
                                            @RequestParam(required = false) Boolean ativa) {
        return congregacaoService.listar(agrupamentoId, subdivisaoId, ativa).stream().map(CongregacaoResponse::de).toList();
    }

    @GetMapping("/{id}")
    public CongregacaoResponse buscarPorId(@PathVariable UUID id) {
        return CongregacaoResponse.de(congregacaoService.buscarPorId(id));
    }

    @PreAuthorize("hasAuthority('CONGREGACAO_GERENCIAR')")
    @PutMapping("/{id}")
    public CongregacaoResponse atualizar(@PathVariable UUID id, @Valid @RequestBody CongregacaoRequest request) {
        return CongregacaoResponse.de(congregacaoService.atualizar(id, request));
    }

    /** Soft delete: marca a congregação como inativa (não remove a linha). */
    @PreAuthorize("hasAuthority('CONGREGACAO_GERENCIAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativar(@PathVariable UUID id) {
        congregacaoService.inativar(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('CONGREGACAO_GERENCIAR')")
    @PatchMapping("/{id}/reativar")
    public CongregacaoResponse reativar(@PathVariable UUID id) {
        return CongregacaoResponse.de(congregacaoService.reativar(id));
    }
}
