package com.registraai.registro_coristas_api.usuario.controller;

import com.registraai.registro_coristas_api.agrupamento.dto.AgrupamentoResponse;
import com.registraai.registro_coristas_api.autenticacao.service.UsuarioLogado;
import com.registraai.registro_coristas_api.usuario.dto.EscopoRequest;
import com.registraai.registro_coristas_api.usuario.dto.EscopoResponse;
import com.registraai.registro_coristas_api.usuario.exception.UsuarioNaoEncontradoException;
import com.registraai.registro_coristas_api.usuario.model.Usuario;
import com.registraai.registro_coristas_api.usuario.service.EscopoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Escopo geográfico do usuário (a quais agrupamentos ele tem acesso). */
@RestController
@RequestMapping("/v1/api/usuarios")
@RequiredArgsConstructor
public class EscopoController {

    private final EscopoService escopoService;
    private final UsuarioLogado usuarioLogado;

    /** O escopo de quem está logado; qualquer usuário autenticado (o front usa para montar o dashboard). */
    @GetMapping("/meu/agrupamentos")
    public EscopoResponse meuEscopo() {
        Usuario usuario = usuarioLogado.atual().orElseThrow(() -> new UsuarioNaoEncontradoException(null));
        return resposta(usuario.getId());
    }

    @PreAuthorize("hasAuthority('ESCOPO_GERENCIAR')")
    @GetMapping("/{id}/agrupamentos")
    public EscopoResponse escopoDe(@PathVariable UUID id) {
        return resposta(id);
    }

    /** Substitui o escopo do usuário (vazio tira todos os vínculos). As regras de quantidade são do Service. */
    @PreAuthorize("hasAuthority('ESCOPO_GERENCIAR')")
    @PutMapping("/{id}/agrupamentos")
    public EscopoResponse definirEscopo(@PathVariable UUID id, @Valid @RequestBody EscopoRequest request) {
        escopoService.definir(id, request.agrupamentoIds());
        return resposta(id);
    }

    private EscopoResponse resposta(UUID usuarioId) {
        return new EscopoResponse(
                usuarioId,
                escopoService.ehGlobal(usuarioId),
                escopoService.agrupamentosDe(usuarioId).stream().map(AgrupamentoResponse::de).toList());
    }
}
