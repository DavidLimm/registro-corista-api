package com.registraai.registro_coristas_api.afastamento.dto;

import com.registraai.registro_coristas_api.afastamento.model.SolicitacaoAfastamento;
import com.registraai.registro_coristas_api.afastamento.model.StatusSolicitacao;

import java.time.Instant;
import java.util.UUID;

public record SolicitacaoAfastamentoResponse(
        UUID id,
        UUID pessoaId,
        String pessoaNome,
        String motivo,
        StatusSolicitacao status,
        UUID solicitadoPor,
        Instant solicitadoEm,
        UUID decididoPor,
        Instant decididoEm
) {

    public static SolicitacaoAfastamentoResponse de(SolicitacaoAfastamento solicitacao) {
        return new SolicitacaoAfastamentoResponse(
                solicitacao.getId(),
                solicitacao.getPessoa().getId(),
                solicitacao.getPessoa().getNome(),
                solicitacao.getMotivo(),
                solicitacao.getStatus(),
                solicitacao.getSolicitadoPor(),
                solicitacao.getSolicitadoEm(),
                solicitacao.getDecididoPor(),
                solicitacao.getDecididoEm()
        );
    }
}
