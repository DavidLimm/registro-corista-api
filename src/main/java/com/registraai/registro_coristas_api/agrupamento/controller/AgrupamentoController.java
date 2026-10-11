package com.registraai.registro_coristas_api.agrupamento.controller;

import com.registraai.registro_coristas_api.agrupamento.dto.AgrupamentoRequest;
import com.registraai.registro_coristas_api.agrupamento.dto.AgrupamentoResponse;
import com.registraai.registro_coristas_api.agrupamento.model.Agrupamento;
import com.registraai.registro_coristas_api.agrupamento.model.TipoAgrupamento;
import com.registraai.registro_coristas_api.agrupamento.service.AgrupamentoService;
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
@RequestMapping("/v1/api/agrupamentos")
@RequiredArgsConstructor
public class AgrupamentoController {

    private final AgrupamentoService agrupamentoService;

    @PreAuthorize("hasAuthority('AGRUPAMENTO_GERENCIAR')")
    @PostMapping
    public ResponseEntity<AgrupamentoResponse> criar(@Valid @RequestBody AgrupamentoRequest request) {
        Agrupamento agrupamento = agrupamentoService.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(agrupamento.getId())
                .toUri();
        return ResponseEntity.created(location).body(AgrupamentoResponse.de(agrupamento));
    }

    /** Público (só leitura): o formulário de auto-cadastro lista tipo → agrupamento → subdivisão → congregação. */
    @GetMapping
    public List<AgrupamentoResponse> listar(@RequestParam(required = false) TipoAgrupamento tipo,
                                            @RequestParam(required = false) Boolean ativo) {
        return agrupamentoService.listar(tipo, ativo).stream().map(AgrupamentoResponse::de).toList();
    }

    @GetMapping("/{id}")
    public AgrupamentoResponse buscarPorId(@PathVariable UUID id) {
        return AgrupamentoResponse.de(agrupamentoService.buscarPorId(id));
    }

    @PreAuthorize("hasAuthority('AGRUPAMENTO_GERENCIAR')")
    @PutMapping("/{id}")
    public AgrupamentoResponse atualizar(@PathVariable UUID id, @Valid @RequestBody AgrupamentoRequest request) {
        return AgrupamentoResponse.de(agrupamentoService.atualizar(id, request));
    }

    /** Soft delete: marca o agrupamento como inativo (não remove a linha). */
    @PreAuthorize("hasAuthority('AGRUPAMENTO_GERENCIAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativar(@PathVariable UUID id) {
        agrupamentoService.inativar(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('AGRUPAMENTO_GERENCIAR')")
    @PatchMapping("/{id}/reativar")
    public AgrupamentoResponse reativar(@PathVariable UUID id) {
        return AgrupamentoResponse.de(agrupamentoService.reativar(id));
    }
}
