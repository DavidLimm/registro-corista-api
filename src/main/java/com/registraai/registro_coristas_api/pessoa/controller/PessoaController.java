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
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
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
     * Paginada e ordenada por nome. Filtros opcionais: nome (trecho), areaId, congregacaoId, faixaEtaria, status.
     */
    @GetMapping
    public PagedModel<PessoaResponse> listar(PessoaFiltro filtro,
                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        LocalDate hoje = hoje();
        return new PagedModel<>(pessoaService.listar(filtro, page, size).map(pessoa -> PessoaResponse.de(pessoa, hoje)));
    }

    @GetMapping("/{id}")
    public PessoaResponse buscarPorId(@PathVariable UUID id) {
        return PessoaResponse.de(pessoaService.buscarPorId(id), hoje());
    }

    /**
     * Status e aprovação não mudam por aqui. Para quem é corista, prefira {@code PUT /v1/api/coristas/{id}}: só ele
     * reclassifica a lista (adolescente/jovem) quando a data de nascimento muda.
     */
    @PutMapping("/{id}")
    public PessoaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody PessoaRequest request) {
        return PessoaResponse.de(pessoaService.atualizar(id, request), hoje());
    }

    /** Soft delete: o cadastro passa a {@code INATIVO} (não remove a linha). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativar(@PathVariable UUID id) {
        pessoaService.inativar(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code PENDENTE -> APROVADO}. TODO(autenticação): {@code aprovadoPor} deve vir do usuário logado, e sua role
     * validada contra o recorte da pessoa (adolescente: {@code DIRIGENTE_UNIAO}+líderes; jovem:
     * {@code LIDERANCA_GRUPO_JOVEM}/{@code DIRIGENTE_CAMPANHA}+líderes) — ainda não há autenticação, então por ora
     * qualquer chamador pode informar qualquer {@code aprovadoPor}.
     */
    @PatchMapping("/{id}/aprovar")
    public PessoaResponse aprovar(@PathVariable UUID id, @RequestParam UUID aprovadoPor) {
        return PessoaResponse.de(pessoaService.aprovar(id, aprovadoPor), hoje());
    }

    /** {@code PENDENTE -> REPROVADO}. Mesma ressalva de {@link #aprovar} sobre a falta de autenticação. */
    @PatchMapping("/{id}/reprovar")
    public PessoaResponse reprovar(@PathVariable UUID id, @RequestParam UUID reprovadoPor) {
        return PessoaResponse.de(pessoaService.reprovar(id, reprovadoPor), hoje());
    }

    private LocalDate hoje() {
        return LocalDate.now(clock);
    }
}
