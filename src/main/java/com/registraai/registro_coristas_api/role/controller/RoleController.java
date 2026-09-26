package com.registraai.registro_coristas_api.role.controller;

import com.registraai.registro_coristas_api.role.dto.RoleRequest;
import com.registraai.registro_coristas_api.role.dto.RoleResponse;
import com.registraai.registro_coristas_api.role.model.Role;
import com.registraai.registro_coristas_api.role.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @PostMapping
    public ResponseEntity<RoleResponse> criar(@Valid @RequestBody RoleRequest request) {
        Role role = roleService.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(role.getId())
                .toUri();
        return ResponseEntity.created(location).body(RoleResponse.de(role));
    }

    @GetMapping
    public List<RoleResponse> listar(@RequestParam(required = false) Boolean ativo) {
        return roleService.listar(ativo).stream().map(RoleResponse::de).toList();
    }

    @GetMapping("/{id}")
    public RoleResponse buscarPorId(@PathVariable UUID id) {
        return RoleResponse.de(roleService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public RoleResponse atualizar(@PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
        return RoleResponse.de(roleService.atualizar(id, request));
    }

    /** Soft delete: marca o papel como inativo (não remove a linha). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativar(@PathVariable UUID id) {
        roleService.inativar(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/reativar")
    public RoleResponse reativar(@PathVariable UUID id) {
        return RoleResponse.de(roleService.reativar(id));
    }
}
