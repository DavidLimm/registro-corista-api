package com.registraai.registro_coristas_api.pessoa.controller;

import com.registraai.registro_coristas_api.pessoa.dto.PessoaFiltro;
import com.registraai.registro_coristas_api.pessoa.dto.PessoaRequest;
import com.registraai.registro_coristas_api.pessoa.dto.PessoaResponse;
import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.pessoa.service.PessoaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import com.registraai.registro_coristas_api.usuario.model.Usuario;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.data.web.PagedModel;
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
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Pessoa sem papel específico (apoio, maestro, pastor...). O corista tem cadastro próprio em
 * {@code /v1/api/coristas}, que grava pessoa e corista juntos.
 */
@RestController
@RequestMapping("/v1/api/pessoas")
@RequiredArgsConstructor
public class PessoaController {

    private final PessoaService pessoaService;
    private final Clock clock;

    /** Entra sempre como {@code PENDENTE}. Menores exigem responsável legal e consentimento LGPD. */
    @PreAuthorize("hasAuthority('PESSOA_CRIAR')")
    @PostMapping
    public ResponseEntity<PessoaResponse> criar(@Valid @RequestBody PessoaRequest request) {
        Pessoa pessoa = pessoaService.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(pessoa.getId())
                .toUri();
        return ResponseEntity.created(location).body(PessoaResponse.de(pessoa, hoje()));
    }

    /**
     * Paginada e ordenada por nome. Filtros opcionais: nome (trecho), agrupamentoId, subdivisaoId, congregacaoId, faixaEtaria, status.
     */
    @PreAuthorize("hasAuthority('PESSOA_LER')")
    @GetMapping
    public PagedModel<PessoaResponse> listar(PessoaFiltro filtro,
                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        LocalDate hoje = hoje();
        return new PagedModel<>(pessoaService.listar(filtro, page, size).map(pessoa -> PessoaResponse.de(pessoa, hoje)));
    }

    @PreAuthorize("hasAuthority('PESSOA_LER')")
    @GetMapping("/{id}")
    public PessoaResponse buscarPorId(@PathVariable UUID id) {
        return PessoaResponse.de(pessoaService.buscarPorId(id), hoje());
    }

    /**
     * Status e aprovação não mudam por aqui. Para quem é corista, prefira {@code PUT /v1/api/coristas/{id}}: só ele
     * reclassifica a lista (adolescente/jovem) quando a data de nascimento muda.
     */
    @PreAuthorize("hasAuthority('PESSOA_ATUALIZAR')")
    @PutMapping("/{id}")
    public PessoaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody PessoaRequest request) {
        return PessoaResponse.de(pessoaService.atualizar(id, request), hoje());
    }

    /** Soft delete: o cadastro passa a {@code INATIVO} (não remove a linha). */
    @PreAuthorize("hasAuthority('PESSOA_INATIVAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativar(@PathVariable UUID id) {
        pessoaService.inativar(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code PENDENTE -> APROVADO}, registrando o usuário logado como aprovador. TODO(escopo): validar a role contra o
     * recorte da pessoa (adolescente: {@code DIRIGENTE_UNIAO}+líderes; jovem:
     * {@code LIDERANCA_GRUPO_JOVEM}/{@code DIRIGENTE_CAMPANHA}+líderes) e contra a área — hoje basta ter a permissão.
     */
    @PreAuthorize("hasAuthority('PESSOA_APROVAR')")
    @PatchMapping("/{id}/aprovar")
    public PessoaResponse aprovar(@PathVariable UUID id, @AuthenticationPrincipal Usuario usuario) {
        return PessoaResponse.de(pessoaService.aprovar(id, usuario.getId()), hoje());
    }

    /** {@code PENDENTE -> REPROVADO}, registrando o usuário logado. Mesma pendência de escopo de {@link #aprovar}. */
    @PreAuthorize("hasAuthority('PESSOA_REPROVAR')")
    @PatchMapping("/{id}/reprovar")
    public PessoaResponse reprovar(@PathVariable UUID id, @AuthenticationPrincipal Usuario usuario) {
        return PessoaResponse.de(pessoaService.reprovar(id, usuario.getId()), hoje());
    }

    /**
     * {@code APROVADO -> AFASTADO}. Só quem tem {@code CORISTA_APROVAR_AFASTAMENTO} (LIDER_MOCIDADE e PASTOR); o
     * usuário logado fica registrado como quem afastou.
     */
    @PreAuthorize("hasAuthority('CORISTA_APROVAR_AFASTAMENTO')")
    @PatchMapping("/{id}/afastar")
    public PessoaResponse afastar(@PathVariable UUID id, @AuthenticationPrincipal Usuario usuario) {
        return PessoaResponse.de(pessoaService.afastar(id, usuario.getId()), hoje());
    }

    /**
     * {@code AFASTADO -> APROVADO}. Mesma permissão de {@link #afastar}; o usuário logado fica registrado.
     */
    @PreAuthorize("hasAuthority('CORISTA_APROVAR_AFASTAMENTO')")
    @PatchMapping("/{id}/retornar-do-afastamento")
    public PessoaResponse retornarDoAfastamento(@PathVariable UUID id, @AuthenticationPrincipal Usuario usuario) {
        return PessoaResponse.de(pessoaService.retornarDoAfastamento(id, usuario.getId()), hoje());
    }

    private LocalDate hoje() {
        return LocalDate.now(clock);
    }
}
