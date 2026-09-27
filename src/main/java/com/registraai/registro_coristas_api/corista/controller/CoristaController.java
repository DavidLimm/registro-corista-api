package com.registraai.registro_coristas_api.corista.controller;

import com.registraai.registro_coristas_api.corista.dto.CoristaFiltro;
import com.registraai.registro_coristas_api.corista.dto.CoristaRequest;
import com.registraai.registro_coristas_api.corista.dto.CoristaResponse;
import com.registraai.registro_coristas_api.corista.model.Corista;
import com.registraai.registro_coristas_api.corista.service.CoristaService;
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

@RestController
@RequestMapping("/v1/api/coristas")
@RequiredArgsConstructor
public class CoristaController {

    private final CoristaService coristaService;
    private final Clock clock;

    @PostMapping
    public ResponseEntity<CoristaResponse> criar(@Valid @RequestBody CoristaRequest request) {
        Corista corista = coristaService.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(corista.getId())
                .toUri();
        return ResponseEntity.created(location).body(CoristaResponse.de(corista, hoje()));
    }

    /** Paginada e ordenada por nome. Filtros opcionais: nome (trecho), areaId, congregacaoId, listaClassificacao, status. */
    @GetMapping
    public PagedModel<CoristaResponse> listar(CoristaFiltro filtro,
                                              @RequestParam(defaultValue = "0") @Min(0) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        LocalDate hoje = hoje();
        return new PagedModel<>(coristaService.listar(filtro, page, size).map(corista -> CoristaResponse.de(corista, hoje)));
    }

    @GetMapping("/{id}")
    public CoristaResponse buscarPorId(@PathVariable UUID id) {
        return CoristaResponse.de(coristaService.buscarPorId(id), hoje());
    }

    @PutMapping("/{id}")
    public CoristaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody CoristaRequest request) {
        return CoristaResponse.de(coristaService.atualizar(id, request), hoje());
    }

    /** Soft delete: o cadastro passa a {@code INATIVO} (não remove a linha). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativar(@PathVariable UUID id) {
        coristaService.inativar(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code PENDENTE -> APROVADO}. TODO(autenticação): {@code aprovadoPor} deve vir do usuário logado, com a role
     * validada contra o recorte (adolescente/jovem) do corista — ver {@code PessoaController.aprovar}.
     */
    @PatchMapping("/{id}/aprovar")
    public CoristaResponse aprovar(@PathVariable UUID id, @RequestParam UUID aprovadoPor) {
        return CoristaResponse.de(coristaService.aprovar(id, aprovadoPor), hoje());
    }

    /** {@code PENDENTE -> REPROVADO}. Nunca muda a lista de classificação (isso é só a promoção antecipada). */
    @PatchMapping("/{id}/reprovar")
    public CoristaResponse reprovar(@PathVariable UUID id, @RequestParam UUID reprovadoPor) {
        return CoristaResponse.de(coristaService.reprovar(id, reprovadoPor), hoje());
    }

    private LocalDate hoje() {
        return LocalDate.now(clock);
    }
}
