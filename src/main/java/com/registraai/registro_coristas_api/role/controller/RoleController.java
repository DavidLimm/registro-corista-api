package com.registraai.registro_coristas_api.role.controller;

import com.registraai.registro_coristas_api.role.dto.RoleRequest;
import com.registraai.registro_coristas_api.role.dto.RoleResponse;
import com.registraai.registro_coristas_api.role.dto.RolePermissoesRequest;
import com.registraai.registro_coristas_api.role.dto.RolePermissoesResponse;
import com.registraai.registro_coristas_api.role.model.Role;
import com.registraai.registro_coristas_api.role.model.StatusRole;
import com.registraai.registro_coristas_api.usuario.model.Usuario;
import com.registraai.registro_coristas_api.role.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @PreAuthorize("hasAuthority('ROLE_GERENCIAR_PERMISSOES')")
    @PostMapping
    public ResponseEntity<RoleResponse> criar(@Valid @RequestBody RoleRequest request) {
        Role role = roleService.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(role.getId())
                .toUri();
        return ResponseEntity.created(location).body(RoleResponse.de(role));
    }

    /** Filtros opcionais: {@code ativo} e {@code status} (ex.: {@code PENDENTE} para os pedidos aguardando decisão). */
    @GetMapping
    public List<RoleResponse> listar(@RequestParam(required = false) Boolean ativo,
                                     @RequestParam(required = false) StatusRole status) {
        return roleService.listar(ativo, status).stream().map(RoleResponse::de).toList();
    }

    /** Pede um papel novo. Fica pendente e inativo até o PASTOR decidir ({@code aprovar}/{@code reprovar}). */
    @PreAuthorize("hasAuthority('ROLE_SOLICITAR')")
    @PostMapping("/solicitacoes")
    public ResponseEntity<RoleResponse> solicitar(@Valid @RequestBody RoleRequest request,
                                                  @AuthenticationPrincipal Usuario usuario) {
        Role role = roleService.solicitar(request, usuario.getId());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/v1/api/roles/{id}")
                .buildAndExpand(role.getId())
                .toUri();
        return ResponseEntity.created(location).body(RoleResponse.de(role));
    }

    @PreAuthorize("hasAuthority('ROLE_APROVAR')")
    @PatchMapping("/{id}/aprovar")
    public RoleResponse aprovar(@PathVariable UUID id, @AuthenticationPrincipal Usuario usuario) {
        return RoleResponse.de(roleService.aprovar(id, usuario.getId()));
    }

    @PreAuthorize("hasAuthority('ROLE_APROVAR')")
    @PatchMapping("/{id}/reprovar")
    public RoleResponse reprovar(@PathVariable UUID id, @AuthenticationPrincipal Usuario usuario) {
        return RoleResponse.de(roleService.reprovar(id, usuario.getId()));
    }

    @PreAuthorize("hasAuthority('ROLE_GERENCIAR_PERMISSOES')")
    @GetMapping("/{id}/permissoes")
    public RolePermissoesResponse permissoes(@PathVariable UUID id) {
        return new RolePermissoesResponse(id, roleService.buscarPorId(id).getNome(), roleService.permissoesDe(id));
    }

    /** Define o conjunto completo de permissões do papel (substitui o anterior). */
    @PreAuthorize("hasAuthority('ROLE_GERENCIAR_PERMISSOES')")
    @PutMapping("/{id}/permissoes")
    public RolePermissoesResponse definirPermissoes(@PathVariable UUID id,
                                                    @Valid @RequestBody RolePermissoesRequest request) {
        Set<String> permissoes = roleService.definirPermissoes(id, request.permissoes());
        return new RolePermissoesResponse(id, roleService.buscarPorId(id).getNome(), permissoes);
    }

    @GetMapping("/{id}")
    public RoleResponse buscarPorId(@PathVariable UUID id) {
        return RoleResponse.de(roleService.buscarPorId(id));
    }

    @PreAuthorize("hasAuthority('ROLE_GERENCIAR_PERMISSOES')")
    @PutMapping("/{id}")
    public RoleResponse atualizar(@PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
        return RoleResponse.de(roleService.atualizar(id, request));
    }

    /** Soft delete: marca o papel como inativo (não remove a linha). */
    @PreAuthorize("hasAuthority('ROLE_GERENCIAR_PERMISSOES')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativar(@PathVariable UUID id) {
        roleService.inativar(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('ROLE_GERENCIAR_PERMISSOES')")
    @PatchMapping("/{id}/reativar")
    public RoleResponse reativar(@PathVariable UUID id) {
        return RoleResponse.de(roleService.reativar(id));
    }
}
