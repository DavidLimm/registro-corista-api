package com.registraai.registro_coristas_api.afastamento.controller;

import com.registraai.registro_coristas_api.afastamento.dto.SolicitacaoAfastamentoRequest;
import com.registraai.registro_coristas_api.afastamento.dto.SolicitacaoAfastamentoResponse;
import com.registraai.registro_coristas_api.afastamento.model.SolicitacaoAfastamento;
import com.registraai.registro_coristas_api.afastamento.model.StatusSolicitacao;
import com.registraai.registro_coristas_api.afastamento.service.SolicitacaoAfastamentoService;
import com.registraai.registro_coristas_api.usuario.model.Usuario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api")
@RequiredArgsConstructor
public class SolicitacaoAfastamentoController {

    private final SolicitacaoAfastamentoService solicitacaoService;

    /** Pede o afastamento do corista. Quem decide é quem tem {@code CORISTA_APROVAR_AFASTAMENTO}. */
    @PreAuthorize("hasAuthority('CORISTA_SOLICITAR_AFASTAMENTO')")
    @PostMapping("/coristas/{id}/solicitar-afastamento")
    public ResponseEntity<SolicitacaoAfastamentoResponse> solicitar(@PathVariable UUID id,
                                                                    @Valid @RequestBody SolicitacaoAfastamentoRequest request,
                                                                    @AuthenticationPrincipal Usuario usuario) {
        SolicitacaoAfastamento solicitacao = solicitacaoService.solicitar(id, request.motivo(), usuario.getId());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/v1/api/solicitacoes-afastamento/{id}")
                .buildAndExpand(solicitacao.getId())
                .toUri();
        return ResponseEntity.created(location).body(SolicitacaoAfastamentoResponse.de(solicitacao));
    }

    /** A fila de quem decide. Filtro opcional {@code status} (ex.: {@code PENDENTE}); mais antigas primeiro. */
    @PreAuthorize("hasAuthority('CORISTA_APROVAR_AFASTAMENTO')")
    @GetMapping("/solicitacoes-afastamento")
    public PagedModel<SolicitacaoAfastamentoResponse> listar(@RequestParam(required = false) StatusSolicitacao status,
                                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return new PagedModel<>(solicitacaoService.listar(status, page, size).map(SolicitacaoAfastamentoResponse::de));
    }

    @PreAuthorize("hasAuthority('CORISTA_APROVAR_AFASTAMENTO')")
    @PatchMapping("/solicitacoes-afastamento/{id}/aprovar")
    public SolicitacaoAfastamentoResponse aprovar(@PathVariable UUID id, @AuthenticationPrincipal Usuario usuario) {
        return SolicitacaoAfastamentoResponse.de(solicitacaoService.aprovar(id, usuario.getId()));
    }

    @PreAuthorize("hasAuthority('CORISTA_APROVAR_AFASTAMENTO')")
    @PatchMapping("/solicitacoes-afastamento/{id}/negar")
    public SolicitacaoAfastamentoResponse negar(@PathVariable UUID id, @AuthenticationPrincipal Usuario usuario) {
        return SolicitacaoAfastamentoResponse.de(solicitacaoService.negar(id, usuario.getId()));
    }
}
