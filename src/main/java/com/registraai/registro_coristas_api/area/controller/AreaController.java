package com.registraai.registro_coristas_api.area.controller;

import com.registraai.registro_coristas_api.area.dto.AreaRequest;
import com.registraai.registro_coristas_api.area.dto.AreaResponse;
import com.registraai.registro_coristas_api.area.model.Area;
import com.registraai.registro_coristas_api.area.service.AreaService;
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
@RequestMapping("/v1/api/areas")
@RequiredArgsConstructor
public class AreaController {

    private final AreaService areaService;

    @PostMapping
    public ResponseEntity<AreaResponse> criar(@Valid @RequestBody AreaRequest request) {
        Area area = areaService.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(area.getId())
                .toUri();
        return ResponseEntity.created(location).body(AreaResponse.de(area));
    }

    @GetMapping
    public List<AreaResponse> listar(@RequestParam(required = false) Boolean ativa) {
        return areaService.listar(ativa).stream().map(AreaResponse::de).toList();
    }

    @GetMapping("/{id}")
    public AreaResponse buscarPorId(@PathVariable UUID id) {
        return AreaResponse.de(areaService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public AreaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody AreaRequest request) {
        return AreaResponse.de(areaService.atualizar(id, request));
    }

    /** Soft delete: marca a área como inativa (não remove a linha). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativar(@PathVariable UUID id) {
        areaService.inativar(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/reativar")
    public AreaResponse reativar(@PathVariable UUID id) {
        return AreaResponse.de(areaService.reativar(id));
    }
}
