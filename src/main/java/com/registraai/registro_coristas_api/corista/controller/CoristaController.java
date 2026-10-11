package com.registraai.registro_coristas_api.corista.controller;

import com.registraai.registro_coristas_api.corista.dto.CoristaFiltro;
import com.registraai.registro_coristas_api.corista.dto.CoristaRequest;
import com.registraai.registro_coristas_api.corista.dto.CoristaVozRequest;
import com.registraai.registro_coristas_api.corista.dto.CoristaResponse;
import com.registraai.registro_coristas_api.corista.model.Corista;
import com.registraai.registro_coristas_api.corista.service.CoristaService;
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

    /** Paginada e ordenada por nome. Filtros opcionais: nome (trecho), agrupamentoId, subdivisaoId, congregacaoId, listaClassificacao, status. */
    @PreAuthorize("hasAuthority('CORISTA_LER')")
    @GetMapping
    public PagedModel<CoristaResponse> listar(CoristaFiltro filtro,
                                              @RequestParam(defaultValue = "0") @Min(0) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        LocalDate hoje = hoje();
        return new PagedModel<>(coristaService.listar(filtro, page, size).map(corista -> CoristaResponse.de(corista, hoje)));
    }

    /** O cadastro de quem está logado. Qualquer usuário autenticado; 404 se ele não for corista. */
    @GetMapping("/meu")
    public CoristaResponse meuCadastro() {
        return CoristaResponse.de(coristaService.buscarDoUsuarioLogado(), hoje());
    }

    /** Liderança (CORISTA_LER) vê qualquer um; os demais, só o próprio cadastro. */
    @PreAuthorize("hasAuthority('CORISTA_LER') or @coristaService.ehProprio(#id)")
    @GetMapping("/{id}")
    public CoristaResponse buscarPorId(@PathVariable UUID id) {
        return CoristaResponse.de(coristaService.buscarPorId(id), hoje());
    }

    @PreAuthorize("hasAuthority('CORISTA_ATUALIZAR') and (hasAuthority('CORISTA_LER') or @coristaService.ehProprio(#id))")
    @PutMapping("/{id}")
    public CoristaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody CoristaRequest request) {
        return CoristaResponse.de(coristaService.atualizar(id, request), hoje());
    }

    /** Só o tipo de voz: é o que o maestro (sem {@code CORISTA_ATUALIZAR}) pode editar, dentro do seu recorte. */
    @PreAuthorize("hasAuthority('CORISTA_ATUALIZAR_VOZ')")
    @PatchMapping("/{id}/voz")
    public CoristaResponse atualizarVoz(@PathVariable UUID id, @Valid @RequestBody CoristaVozRequest request) {
        return CoristaResponse.de(coristaService.atualizarVoz(id, request.tipoVoz()), hoje());
    }

    /** Soft delete: o cadastro passa a {@code INATIVO} (não remove a linha). */
    @PreAuthorize("hasAuthority('CORISTA_INATIVAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativar(@PathVariable UUID id) {
        coristaService.inativar(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code PENDENTE -> APROVADO}, registrando o usuário logado. TODO(escopo): validar a role contra o recorte
     * (adolescente/jovem) do corista — ver {@code PessoaController.aprovar}.
     */
    @PreAuthorize("hasAuthority('CORISTA_APROVAR')")
    @PatchMapping("/{id}/aprovar")
    public CoristaResponse aprovar(@PathVariable UUID id, @AuthenticationPrincipal Usuario usuario) {
        return CoristaResponse.de(coristaService.aprovar(id, usuario.getId()), hoje());
    }

    /** {@code PENDENTE -> REPROVADO}. Nunca muda a lista de classificação (isso é só a promoção antecipada). */
    @PreAuthorize("hasAuthority('CORISTA_REPROVAR')")
    @PatchMapping("/{id}/reprovar")
    public CoristaResponse reprovar(@PathVariable UUID id, @AuthenticationPrincipal Usuario usuario) {
        return CoristaResponse.de(coristaService.reprovar(id, usuario.getId()), hoje());
    }

    /**
     * Promoção antecipada {@code ADOLESCENTE -> JOVEM}: só nesse sentido, permanente, com trilha (quem promoveu é o
     * usuário logado). A pessoa continua menor de idade, então as proteções LGPD seguem valendo.
     */
    @PreAuthorize("hasAuthority('CORISTA_PROMOVER')")
    @PatchMapping("/{id}/promover")
    public CoristaResponse promover(@PathVariable UUID id, @AuthenticationPrincipal Usuario usuario) {
        return CoristaResponse.de(coristaService.promover(id, usuario.getId()), hoje());
    }

    /**
     * {@code APROVADO -> AFASTADO}. Só quem tem {@code CORISTA_APROVAR_AFASTAMENTO}; ver
     * {@code PessoaController.afastar}.
     */
    @PreAuthorize("hasAuthority('CORISTA_APROVAR_AFASTAMENTO')")
    @PatchMapping("/{id}/afastar")
    public CoristaResponse afastar(@PathVariable UUID id, @AuthenticationPrincipal Usuario usuario) {
        return CoristaResponse.de(coristaService.afastar(id, usuario.getId()), hoje());
    }

    /** {@code AFASTADO -> APROVADO}. Mesma ressalva de {@link #afastar}. */
    @PreAuthorize("hasAuthority('CORISTA_APROVAR_AFASTAMENTO')")
    @PatchMapping("/{id}/retornar-do-afastamento")
    public CoristaResponse retornarDoAfastamento(@PathVariable UUID id, @AuthenticationPrincipal Usuario usuario) {
        return CoristaResponse.de(coristaService.retornarDoAfastamento(id, usuario.getId()), hoje());
    }

    private LocalDate hoje() {
        return LocalDate.now(clock);
    }
}
