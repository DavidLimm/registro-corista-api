package com.registraai.registro_coristas_api.usuario.controller;

import com.registraai.registro_coristas_api.usuario.dto.AppUserRequest;
import com.registraai.registro_coristas_api.usuario.dto.AppUserResponse;
import com.registraai.registro_coristas_api.usuario.model.AppUser;
import com.registraai.registro_coristas_api.usuario.service.AppUserService;
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
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/usuarios")
@RequiredArgsConstructor
public class AppUserController {

    private final AppUserService appUserService;

    @PostMapping
    public ResponseEntity<AppUserResponse> criar(@Valid @RequestBody AppUserRequest request) {
        AppUser appUser = appUserService.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(appUser.getId())
                .toUri();
        return ResponseEntity.created(location).body(AppUserResponse.de(appUser));
    }

    /** Paginada e ordenada por e-mail. Filtro opcional: {@code ativo}. */
    @GetMapping
    public PagedModel<AppUserResponse> listar(@RequestParam(required = false) Boolean ativo,
                                              @RequestParam(defaultValue = "0") @Min(0) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return new PagedModel<>(appUserService.listar(ativo, page, size).map(AppUserResponse::de));
    }

    @GetMapping("/{id}")
    public AppUserResponse buscarPorId(@PathVariable UUID id) {
        return AppUserResponse.de(appUserService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public AppUserResponse atualizar(@PathVariable UUID id, @Valid @RequestBody AppUserRequest request) {
        return AppUserResponse.de(appUserService.atualizar(id, request));
    }

    /** Soft delete: marca o usuário como inativo (não remove a linha). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativar(@PathVariable UUID id) {
        appUserService.inativar(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/reativar")
    public AppUserResponse reativar(@PathVariable UUID id) {
        return AppUserResponse.de(appUserService.reativar(id));
    }
}
