package com.registraai.registro_coristas_api.usuario.service;

import com.registraai.registro_coristas_api.autenticacao.exception.ForaDoEscopoException;
import com.registraai.registro_coristas_api.agrupamento.model.Subdivisao;
import com.registraai.registro_coristas_api.autenticacao.service.UsuarioLogado;
import com.registraai.registro_coristas_api.congregacao.model.Congregacao;
import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.usuario.model.Usuario;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * O recorte geográfico de quem está logado, aplicado no backend a cada operação: ADMIN e PASTOR_ADMIN enxergam tudo;
 * os demais só o que pertence aos agrupamentos a que estão vinculados ({@code usuario_agrupamento}). Sem usuário
 * autenticado, ou sem vínculo, não se enxerga nada (falha fechada).
 *
 * <p>É uma camada a mais sobre a permissão ({@code CORISTA_LER}...) e o recorte por lista ({@code RecorteDeAcesso}):
 * a permissão diz <i>o que</i> se pode fazer, e este recorte diz <i>onde</i>.
 */
@Component
@RequiredArgsConstructor
public class EscopoDeAcesso {

    private final UsuarioLogado usuarioLogado;
    private final EscopoService escopoService;

    private record Alcance(boolean total, Set<UUID> agrupamentos) {
    }

    private Alcance alcance() {
        Usuario ator = usuarioLogado.atual().orElse(null);
        if (ator == null) {
            return new Alcance(false, Set.of());
        }
        if (Papeis.ehGlobal(ator)) {
            return new Alcance(true, Set.of());
        }
        return new Alcance(false, escopoService.idsDosAgrupamentosDe(ator.getId()));
    }

    public boolean foraDoEscopo(UUID agrupamentoId) {
        Alcance alcance = alcance();
        return !alcance.total() && !alcance.agrupamentos().contains(agrupamentoId);
    }

    public boolean fora(Congregacao congregacao) {
        return foraDoEscopo(congregacao.getSubdivisao().getAgrupamento().getId());
    }

    public boolean fora(Pessoa pessoa) {
        return fora(pessoa.getCongregacao());
    }

    /** 403 quando a subdivisão, onde uma congregação vai entrar, está fora do escopo. */
    public void exigirDentro(Subdivisao subdivisao) {
        if (foraDoEscopo(subdivisao.getAgrupamento().getId())) {
            throw new ForaDoEscopoException();
        }
    }

    /** Para recursos não secretos (ex.: congregações): 403 quando fora do escopo. */
    public void exigirDentro(Congregacao congregacao) {
        if (fora(congregacao)) {
            throw new ForaDoEscopoException();
        }
    }

    /**
     * Restringe uma consulta ao escopo: {@code agrupamentoId} diz onde está o id do agrupamento a partir da raiz
     * (ex.: {@code root.get("congregacao").get("subdivisao").get("agrupamento").get("id")}). Quem vê tudo não é
     * restringido; quem não tem vínculo não recebe nenhuma linha.
     */
    public <T> Specification<T> restringirA(Function<Root<T>, Expression<?>> agrupamentoId) {
        Alcance alcance = alcance();
        return (raiz, consulta, cb) -> {
            if (alcance.total()) {
                return cb.conjunction();
            }
            if (alcance.agrupamentos().isEmpty()) {
                return cb.disjunction();
            }
            Predicate dentro = agrupamentoId.apply(raiz).in(alcance.agrupamentos());
            return dentro;
        };
    }
}
