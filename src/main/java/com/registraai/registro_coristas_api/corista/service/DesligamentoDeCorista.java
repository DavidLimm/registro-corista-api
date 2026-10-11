package com.registraai.registro_coristas_api.corista.service;

import com.registraai.registro_coristas_api.corista.model.MotivoDesligamento;
import com.registraai.registro_coristas_api.corista.repository.CoristaRepository;
import com.registraai.registro_coristas_api.usuario.event.PessoaVirouLiderEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/** Fecha o cadastro de corista de quem virou líder. Roda na transação de quem publicou o evento. */
@Component
@RequiredArgsConstructor
public class DesligamentoDeCorista {

    private final CoristaRepository coristaRepository;
    private final Clock clock;

    @EventListener
    public void aoVirarLider(PessoaVirouLiderEvent evento) {
        coristaRepository.findByPessoaId(evento.pessoaId())
                .filter(corista -> !corista.desligado())
                .ifPresent(corista -> {
                    corista.setDesligadoEm(Instant.now(clock));
                    corista.setMotivoDesligamento(MotivoDesligamento.VIROU_LIDER);
                    coristaRepository.save(corista);
                });
    }
}
