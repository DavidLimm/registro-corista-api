package com.registraai.registro_coristas_api.agrupamento.service;

import com.registraai.registro_coristas_api.agrupamento.dto.AgrupamentoRequest;
import com.registraai.registro_coristas_api.agrupamento.exception.AgrupamentoDuplicadoException;
import com.registraai.registro_coristas_api.agrupamento.exception.AgrupamentoInvalidoException;
import com.registraai.registro_coristas_api.agrupamento.exception.AgrupamentoNaoEncontradoException;
import com.registraai.registro_coristas_api.agrupamento.exception.AgrupamentoPossuiCongregacoesException;
import com.registraai.registro_coristas_api.agrupamento.model.Agrupamento;
import com.registraai.registro_coristas_api.agrupamento.model.Subdivisao;
import com.registraai.registro_coristas_api.agrupamento.model.TipoAgrupamento;
import com.registraai.registro_coristas_api.agrupamento.repository.AgrupamentoRepository;
import com.registraai.registro_coristas_api.agrupamento.repository.SubdivisaoRepository;
import com.registraai.registro_coristas_api.congregacao.repository.CongregacaoRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgrupamentoService {

    private final AgrupamentoRepository agrupamentoRepository;
    private final SubdivisaoRepository subdivisaoRepository;
    private final CongregacaoRepository congregacaoRepository;

    /** Tipo AREA nasce com a sua única subdivisão implícita, para a congregação sempre apontar para uma subdivisão. */
    @Transactional
    public Agrupamento criar(AgrupamentoRequest request) {
        Agrupamento agrupamento = new Agrupamento();
        agrupamento.setTipo(request.tipo());
        preencher(agrupamento, request);
        garantirSemDuplicidade(agrupamento, null);
        Agrupamento salvo = agrupamentoRepository.save(agrupamento);
        if (salvo.getTipo() == TipoAgrupamento.AREA) {
            subdivisaoRepository.save(Subdivisao.builder().agrupamento(salvo).build());
        }
        return salvo;
    }

    @Transactional(readOnly = true)
    public Agrupamento buscarPorId(UUID id) {
        return agrupamentoRepository.findById(id)
                .orElseThrow(() -> new AgrupamentoNaoEncontradoException(id));
    }

    /** @param tipo {@code null} lista todos os tipos; @param ativo {@code null} lista ativos e inativos. */
    @Transactional(readOnly = true)
    public List<Agrupamento> listar(TipoAgrupamento tipo, Boolean ativo) {
        Specification<Agrupamento> filtro = (raiz, consulta, cb) -> {
            List<Predicate> condicoes = new ArrayList<>();
            if (tipo != null) {
                condicoes.add(cb.equal(raiz.get("tipo"), tipo));
            }
            if (ativo != null) {
                condicoes.add(cb.equal(raiz.get("ativo"), ativo));
            }
            return cb.and(condicoes.toArray(new Predicate[0]));
        };
        return agrupamentoRepository.findAll(filtro, Sort.by("tipo", "numero", "nome"));
    }

    /** O tipo não muda depois de criado: trocar o tipo mudaria os campos obrigatórios e as subdivisões. */
    @Transactional
    public Agrupamento atualizar(UUID id, AgrupamentoRequest request) {
        Agrupamento agrupamento = buscarPorId(id);
        if (agrupamento.getTipo() != request.tipo()) {
            throw new AgrupamentoInvalidoException("O tipo do agrupamento não pode ser alterado");
        }
        preencher(agrupamento, request);
        garantirSemDuplicidade(agrupamento, id);
        return agrupamentoRepository.save(agrupamento);
    }

    /**
     * Soft delete: o agrupamento permanece no banco com {@code ativo = false}. Só é permitido se não restar
     * nenhuma congregação ativa nele; elas precisam ser remanejadas antes.
     */
    @Transactional
    public void inativar(UUID id) {
        Agrupamento agrupamento = buscarPorId(id);
        long congregacoesAtivas = congregacaoRepository.contarAtivasPorAgrupamento(id);
        if (congregacoesAtivas > 0) {
            throw new AgrupamentoPossuiCongregacoesException(descrever(agrupamento), congregacoesAtivas);
        }
        agrupamento.setAtivo(false);
        agrupamentoRepository.save(agrupamento);
    }

    @Transactional
    public Agrupamento reativar(UUID id) {
        Agrupamento agrupamento = buscarPorId(id);
        agrupamento.setAtivo(true);
        return agrupamentoRepository.save(agrupamento);
    }

    /** Nome legível para mensagens: "Área 40", "Setor 1 (Cabo)", "Filial Petrolina", "Campo missionário Argentina". */
    public static String descrever(Agrupamento agrupamento) {
        return switch (agrupamento.getTipo()) {
            case AREA -> "a Área " + agrupamento.getNumero();
            case SETOR -> "o Setor " + agrupamento.getNumero() + " (" + agrupamento.getNome() + ")";
            case FILIAL -> "a Filial " + agrupamento.getNome();
            case CAMPO_MISSIONARIO -> "o Campo Missionário " + agrupamento.getNome();
        };
    }

    /** Campos obrigatórios por tipo; campo que não pertence ao tipo é recusado em vez de ignorado em silêncio. */
    private void preencher(Agrupamento agrupamento, AgrupamentoRequest request) {
        TipoAgrupamento tipo = agrupamento.getTipo();
        String nome = limpar(request.nome());
        String pais = limpar(request.pais());
        Integer numero = request.numero();

        switch (tipo) {
            case CAMPO_MISSIONARIO -> {
                exigir(nome != null, tipo, "nome");
                exigir(pais != null, tipo, "país");
                proibir(numero == null, tipo, "número");
            }
            case FILIAL -> {
                exigir(nome != null, tipo, "nome");
                proibir(numero == null, tipo, "número");
                proibir(pais == null, tipo, "país");
            }
            case SETOR -> {
                exigir(numero != null, tipo, "número");
                exigir(nome != null, tipo, "nome");
                proibir(pais == null, tipo, "país");
            }
            case AREA -> {
                exigir(numero != null, tipo, "número");
                proibir(nome == null, tipo, "nome");
                proibir(pais == null, tipo, "país");
            }
        }
        agrupamento.setNome(nome);
        agrupamento.setNumero(numero);
        agrupamento.setPais(pais);
    }

    private void garantirSemDuplicidade(Agrupamento agrupamento, UUID idAtual) {
        TipoAgrupamento tipo = agrupamento.getTipo();
        boolean porNumero = tipo == TipoAgrupamento.SETOR || tipo == TipoAgrupamento.AREA;
        boolean duplicado;
        if (porNumero) {
            duplicado = idAtual == null
                    ? agrupamentoRepository.existsByTipoAndNumero(tipo, agrupamento.getNumero())
                    : agrupamentoRepository.existsByTipoAndNumeroAndIdNot(tipo, agrupamento.getNumero(), idAtual);
            if (duplicado) {
                throw new AgrupamentoDuplicadoException("Já existe " + rotuloDoTipo(tipo) + " com o número " + agrupamento.getNumero());
            }
        } else {
            duplicado = idAtual == null
                    ? agrupamentoRepository.existsByTipoAndNomeIgnoreCase(tipo, agrupamento.getNome())
                    : agrupamentoRepository.existsByTipoAndNomeIgnoreCaseAndIdNot(tipo, agrupamento.getNome(), idAtual);
            if (duplicado) {
                throw new AgrupamentoDuplicadoException("Já existe " + rotuloDoTipo(tipo) + " com o nome " + agrupamento.getNome());
            }
        }
    }

    private static String rotuloDoTipo(TipoAgrupamento tipo) {
        return switch (tipo) {
            case AREA -> "uma Área";
            case SETOR -> "um Setor";
            case FILIAL -> "uma Filial";
            case CAMPO_MISSIONARIO -> "um Campo Missionário";
        };
    }

    private static void exigir(boolean presente, TipoAgrupamento tipo, String campo) {
        if (!presente) {
            throw new AgrupamentoInvalidoException("O campo " + campo + " é obrigatório para o tipo " + tipo);
        }
    }

    private static void proibir(boolean ausente, TipoAgrupamento tipo, String campo) {
        if (!ausente) {
            throw new AgrupamentoInvalidoException("O campo " + campo + " não se aplica ao tipo " + tipo);
        }
    }

    private static String limpar(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.trim();
    }
}
