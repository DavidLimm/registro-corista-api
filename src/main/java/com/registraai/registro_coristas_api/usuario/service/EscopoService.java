package com.registraai.registro_coristas_api.usuario.service;

import com.registraai.registro_coristas_api.agrupamento.exception.AgrupamentoNaoEncontradoException;
import com.registraai.registro_coristas_api.agrupamento.model.Agrupamento;
import com.registraai.registro_coristas_api.agrupamento.model.TipoAgrupamento;
import com.registraai.registro_coristas_api.agrupamento.repository.AgrupamentoRepository;
import com.registraai.registro_coristas_api.autenticacao.service.UsuarioLogado;
import com.registraai.registro_coristas_api.usuario.exception.EscopoInvalidoException;
import com.registraai.registro_coristas_api.usuario.exception.UsuarioNaoEncontradoException;
import com.registraai.registro_coristas_api.usuario.model.Usuario;
import com.registraai.registro_coristas_api.usuario.model.UsuarioAgrupamento;
import com.registraai.registro_coristas_api.usuario.repository.UsuarioAgrupamentoRepository;
import com.registraai.registro_coristas_api.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Escopo geográfico do usuário: a quais agrupamentos ele tem acesso.
 * <ul>
 *   <li>{@code ADMIN} e {@code PASTOR_ADMIN}: global, sem vínculos.</li>
 *   <li>{@code PASTOR}: até {@value #MAXIMO_DO_PASTOR} agrupamentos, de quaisquer tipos — exceto que, se um deles é
 *       {@code CAMPO_MISSIONARIO}, o pastor fica apenas nele.</li>
 *   <li>Demais papéis: exatamente 1.</li>
 * </ul>
 * A quantidade é regra daqui, não constraint de banco.
 */
@Service
@RequiredArgsConstructor
public class EscopoService {

    static final int MAXIMO_DO_PASTOR = 2;

    private final UsuarioRepository usuarioRepository;
    private final AgrupamentoRepository agrupamentoRepository;
    private final UsuarioAgrupamentoRepository vinculoRepository;
    private final UsuarioLogado usuarioLogado;

    @Transactional(readOnly = true)
    public List<Agrupamento> agrupamentosDe(UUID usuarioId) {
        return vinculoRepository.findAllByUsuarioId(usuarioId).stream().map(UsuarioAgrupamento::getAgrupamento).toList();
    }

    /** Ids dos agrupamentos do usuário (a base para filtrar consultas pelo escopo). */
    @Transactional(readOnly = true)
    public Set<UUID> idsDosAgrupamentosDe(UUID usuarioId) {
        return agrupamentosDe(usuarioId).stream().map(Agrupamento::getId).collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public boolean ehGlobal(UUID usuarioId) {
        return Papeis.ehGlobal(buscarUsuario(usuarioId));
    }

    /** Substitui o escopo do usuário. Quem faz é o usuário logado (fica na trilha {@code vinculado_por}). */
    @Transactional
    public List<Agrupamento> definir(UUID usuarioId, Set<UUID> agrupamentoIds) {
        Usuario usuario = buscarUsuario(usuarioId);
        if (Papeis.ehGlobal(usuario)) {
            throw new EscopoInvalidoException(
                    "ADMIN e PASTOR_ADMIN têm acesso global e não recebem agrupamentos");
        }
        List<Agrupamento> novos = buscarAtivos(agrupamentoIds);
        validarQuantidade(usuario, novos);

        Set<UUID> atuais = idsDosAgrupamentosDe(usuarioId);
        List<UUID> aRemover = atuais.stream().filter(id -> !agrupamentoIds.contains(id)).toList();
        if (!aRemover.isEmpty()) {
            vinculoRepository.removerDoUsuario(usuarioId, aRemover);
        }
        UUID autor = usuarioLogado.atual().map(Usuario::getId).orElse(null);
        List<UsuarioAgrupamento> aAdicionar = new ArrayList<>();
        for (Agrupamento agrupamento : novos) {
            if (!atuais.contains(agrupamento.getId())) {
                aAdicionar.add(UsuarioAgrupamento.builder()
                        .usuario(usuario).agrupamento(agrupamento).vinculadoPor(autor).build());
            }
        }
        vinculoRepository.saveAll(aAdicionar);
        return novos;
    }

    /**
     * Mantém o escopo coerente com os papéis, depois de criar o usuário ou trocar os papéis dele: quem vira global
     * perde os vínculos; quem não é pastor e está sem vínculo recebe o agrupamento da congregação da pessoa (é o que
     * o auto-cadastro precisa); e quem já tem mais vínculos do que o novo papel permite barra a troca.
     */
    @Transactional
    public void ajustarAoPapel(Usuario usuario) {
        if (Papeis.ehGlobal(usuario)) {
            vinculoRepository.removerTodosDoUsuario(usuario.getId());
            return;
        }
        List<Agrupamento> atuais = agrupamentosDe(usuario.getId());
        if (atuais.isEmpty()) {
            if (!Papeis.tem(usuario, Papeis.PASTOR)) {
                Agrupamento daCongregacao = usuario.getPessoa().getCongregacao().getSubdivisao().getAgrupamento();
                vinculoRepository.save(UsuarioAgrupamento.builder()
                        .usuario(usuario).agrupamento(daCongregacao)
                        .vinculadoPor(usuarioLogado.atual().map(Usuario::getId).orElse(null)).build());
            }
            return;
        }
        validarQuantidade(usuario, atuais);
    }

    private void validarQuantidade(Usuario usuario, List<Agrupamento> agrupamentos) {
        int total = agrupamentos.size();
        if (!Papeis.tem(usuario, Papeis.PASTOR)) {
            if (total != 1) {
                throw new EscopoInvalidoException("Este papel exige exatamente 1 agrupamento (informados: " + total + ")");
            }
            return;
        }
        if (total > MAXIMO_DO_PASTOR) {
            throw new EscopoInvalidoException("O pastor pode ter no máximo " + MAXIMO_DO_PASTOR + " agrupamentos");
        }
        boolean temCampo = agrupamentos.stream().anyMatch(a -> a.getTipo() == TipoAgrupamento.CAMPO_MISSIONARIO);
        if (temCampo && total > 1) {
            throw new EscopoInvalidoException("O pastor de um Campo Missionário fica apenas nele");
        }
    }

    private List<Agrupamento> buscarAtivos(Set<UUID> ids) {
        List<Agrupamento> encontrados = agrupamentoRepository.findAllById(ids);
        if (encontrados.size() < ids.size()) {
            Set<UUID> existentes = encontrados.stream().map(Agrupamento::getId).collect(Collectors.toSet());
            UUID faltante = ids.stream().filter(id -> !existentes.contains(id)).findFirst().orElseThrow();
            throw new AgrupamentoNaoEncontradoException(faltante);
        }
        if (encontrados.stream().anyMatch(a -> !a.isAtivo())) {
            throw new EscopoInvalidoException("Não é possível vincular um agrupamento inativo");
        }
        return encontrados;
    }

    private Usuario buscarUsuario(UUID id) {
        return usuarioRepository.findById(id).orElseThrow(() -> new UsuarioNaoEncontradoException(id));
    }
}
