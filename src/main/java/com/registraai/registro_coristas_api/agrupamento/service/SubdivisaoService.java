package com.registraai.registro_coristas_api.agrupamento.service;

import com.registraai.registro_coristas_api.agrupamento.dto.SubdivisaoRequest;
import com.registraai.registro_coristas_api.agrupamento.exception.AgrupamentoDuplicadoException;
import com.registraai.registro_coristas_api.agrupamento.exception.AgrupamentoInativoException;
import com.registraai.registro_coristas_api.agrupamento.exception.AgrupamentoInvalidoException;
import com.registraai.registro_coristas_api.agrupamento.exception.AgrupamentoNaoEncontradoException;
import com.registraai.registro_coristas_api.agrupamento.exception.AgrupamentoPossuiCongregacoesException;
import com.registraai.registro_coristas_api.agrupamento.exception.SubdivisaoNaoEncontradaException;
import com.registraai.registro_coristas_api.agrupamento.model.Agrupamento;
import com.registraai.registro_coristas_api.agrupamento.model.Subdivisao;
import com.registraai.registro_coristas_api.agrupamento.model.TipoAgrupamento;
import com.registraai.registro_coristas_api.agrupamento.repository.AgrupamentoRepository;
import com.registraai.registro_coristas_api.agrupamento.repository.SubdivisaoRepository;
import com.registraai.registro_coristas_api.congregacao.repository.CongregacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Campo missionário: subdivisão ("Local") só com nome. Filial e setor: subdivisão (Área) só com número.
 * Tipo AREA: a única subdivisão é implícita e criada junto do agrupamento, então não se mexe nela por aqui.
 */
@Service
@RequiredArgsConstructor
public class SubdivisaoService {

    private final AgrupamentoRepository agrupamentoRepository;
    private final SubdivisaoRepository subdivisaoRepository;
    private final CongregacaoRepository congregacaoRepository;

    @Transactional
    public Subdivisao criar(UUID agrupamentoId, SubdivisaoRequest request) {
        Agrupamento agrupamento = buscarAgrupamento(agrupamentoId);
        if (!agrupamento.isAtivo()) {
            throw new AgrupamentoInativoException();
        }
        recusarSeImplicita(agrupamento);

        Subdivisao subdivisao = Subdivisao.builder().agrupamento(agrupamento).build();
        preencher(subdivisao, request);
        garantirSemDuplicidade(subdivisao, null);
        return subdivisaoRepository.save(subdivisao);
    }

    @Transactional(readOnly = true)
    public Subdivisao buscar(UUID agrupamentoId, UUID id) {
        return subdivisaoRepository.findByIdAndAgrupamentoId(id, agrupamentoId)
                .orElseThrow(() -> new SubdivisaoNaoEncontradaException(id));
    }

    /** @param ativo {@code null} lista ativas e inativas. */
    @Transactional(readOnly = true)
    public List<Subdivisao> listar(UUID agrupamentoId, Boolean ativo) {
        buscarAgrupamento(agrupamentoId);
        if (ativo == null) {
            return subdivisaoRepository.findAllByAgrupamentoIdOrderByNumeroAscNomeAsc(agrupamentoId);
        }
        return subdivisaoRepository.findAllByAgrupamentoIdAndAtivoOrderByNumeroAscNomeAsc(agrupamentoId, ativo);
    }

    @Transactional
    public Subdivisao atualizar(UUID agrupamentoId, UUID id, SubdivisaoRequest request) {
        Subdivisao subdivisao = buscar(agrupamentoId, id);
        recusarSeImplicita(subdivisao.getAgrupamento());
        preencher(subdivisao, request);
        garantirSemDuplicidade(subdivisao, id);
        return subdivisaoRepository.save(subdivisao);
    }

    /** Soft delete; só se não houver congregação ativa nela. */
    @Transactional
    public void inativar(UUID agrupamentoId, UUID id) {
        Subdivisao subdivisao = buscar(agrupamentoId, id);
        recusarSeImplicita(subdivisao.getAgrupamento());
        long congregacoesAtivas = congregacaoRepository.contarAtivasPorSubdivisao(id);
        if (congregacoesAtivas > 0) {
            throw new AgrupamentoPossuiCongregacoesException(descrever(subdivisao), congregacoesAtivas);
        }
        subdivisao.setAtivo(false);
        subdivisaoRepository.save(subdivisao);
    }

    @Transactional
    public Subdivisao reativar(UUID agrupamentoId, UUID id) {
        Subdivisao subdivisao = buscar(agrupamentoId, id);
        recusarSeImplicita(subdivisao.getAgrupamento());
        subdivisao.setAtivo(true);
        return subdivisaoRepository.save(subdivisao);
    }

    private Agrupamento buscarAgrupamento(UUID id) {
        return agrupamentoRepository.findById(id)
                .orElseThrow(() -> new AgrupamentoNaoEncontradoException(id));
    }

    private void recusarSeImplicita(Agrupamento agrupamento) {
        if (agrupamento.getTipo() == TipoAgrupamento.AREA) {
            throw new AgrupamentoInvalidoException(
                    "A Área tem uma única subdivisão implícita; ative ou inative o próprio agrupamento");
        }
    }

    private void preencher(Subdivisao subdivisao, SubdivisaoRequest request) {
        TipoAgrupamento tipo = subdivisao.getAgrupamento().getTipo();
        String nome = request.nome() == null || request.nome().isBlank() ? null : request.nome().trim();
        Integer numero = request.numero();

        if (tipo == TipoAgrupamento.CAMPO_MISSIONARIO) {
            if (nome == null) {
                throw new AgrupamentoInvalidoException("O Local de um Campo Missionário exige nome");
            }
            if (numero != null) {
                throw new AgrupamentoInvalidoException("O Local de um Campo Missionário não tem número");
            }
        } else {
            if (numero == null) {
                throw new AgrupamentoInvalidoException("A Área de um " + tipo + " exige número");
            }
            if (nome != null) {
                throw new AgrupamentoInvalidoException("A Área de um " + tipo + " não tem nome");
            }
        }
        subdivisao.setNome(nome);
        subdivisao.setNumero(numero);
    }

    private void garantirSemDuplicidade(Subdivisao subdivisao, UUID idAtual) {
        UUID agrupamentoId = subdivisao.getAgrupamento().getId();
        if (subdivisao.getNumero() != null) {
            boolean duplicado = idAtual == null
                    ? subdivisaoRepository.existsByAgrupamentoIdAndNumero(agrupamentoId, subdivisao.getNumero())
                    : subdivisaoRepository.existsByAgrupamentoIdAndNumeroAndIdNot(agrupamentoId, subdivisao.getNumero(), idAtual);
            if (duplicado) {
                throw new AgrupamentoDuplicadoException("Já existe uma subdivisão com o número " + subdivisao.getNumero() + " neste agrupamento");
            }
        } else {
            boolean duplicado = idAtual == null
                    ? subdivisaoRepository.existsByAgrupamentoIdAndNomeIgnoreCase(agrupamentoId, subdivisao.getNome())
                    : subdivisaoRepository.existsByAgrupamentoIdAndNomeIgnoreCaseAndIdNot(agrupamentoId, subdivisao.getNome(), idAtual);
            if (duplicado) {
                throw new AgrupamentoDuplicadoException("Já existe uma subdivisão com o nome " + subdivisao.getNome() + " neste agrupamento");
            }
        }
    }

    private static String descrever(Subdivisao subdivisao) {
        return subdivisao.getNumero() != null
                ? "a Área " + subdivisao.getNumero()
                : "o Local " + subdivisao.getNome();
    }
}
