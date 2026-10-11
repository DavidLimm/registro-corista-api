package com.registraai.registro_coristas_api.corista.service;

import com.registraai.registro_coristas_api.corista.model.ListaClassificacao;
import com.registraai.registro_coristas_api.role.model.Role;
import com.registraai.registro_coristas_api.usuario.model.Usuario;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.registraai.registro_coristas_api.corista.model.ListaClassificacao.ADOLESCENTE;
import static com.registraai.registro_coristas_api.corista.model.ListaClassificacao.JOVEM;

/**
 * De quais listas (adolescentes e/ou jovens) cada papel enxerga e age sobre coristas. A permissão diz <i>o que</i> o
 * papel pode fazer ({@code CORISTA_LER}, {@code CORISTA_APROVAR}...); o recorte diz <i>sobre quem</i>. Papel que não
 * está aqui (ex.: {@code CORISTA_*}, ou um papel novo criado pelo PASTOR) não tem recorte: só acessa o próprio cadastro.
 *
 * <p>Por ora o recorte é só por lista; o vínculo por agrupamento/congregação entra com a hierarquia organizacional.
 */
final class RecorteDeAcesso {

    private static final Set<ListaClassificacao> AMBAS = EnumSet.of(ADOLESCENTE, JOVEM);
    private static final Set<ListaClassificacao> SO_ADOLESCENTES = EnumSet.of(ADOLESCENTE);
    private static final Set<ListaClassificacao> SO_JOVENS = EnumSet.of(JOVEM);

    private static final Map<String, Set<ListaClassificacao>> POR_PAPEL = Map.ofEntries(
            Map.entry("ADMIN", AMBAS),
            Map.entry("PASTOR", AMBAS),
            Map.entry("PRESBITERO", AMBAS),
            Map.entry("LIDER_MOCIDADE", AMBAS),
            Map.entry("LIDER_ADOLESCENTES", SO_ADOLESCENTES),
            Map.entry("DIRIGENTE_UNIAO", SO_ADOLESCENTES),
            Map.entry("APOIO_ADOLESCENTES", SO_ADOLESCENTES),
            Map.entry("MAESTRO_ADOLESCENTES", SO_ADOLESCENTES),
            Map.entry("LIDER_JOVENS", SO_JOVENS),
            Map.entry("LIDERANCA_GRUPO_JOVEM", SO_JOVENS),
            Map.entry("DIRIGENTE_CAMPANHA", SO_JOVENS),
            Map.entry("APOIO_JOVENS", SO_JOVENS),
            Map.entry("MAESTRO_JOVENS", SO_JOVENS));

    private RecorteDeAcesso() {
    }

    /**
     * Tem papel de corista ({@code CORISTA_ADOLESCENTES}/{@code CORISTA_JOVENS})? Quem virou líder perde o papel de
     * corista e, com ele, o acesso ao próprio cadastro de corista (nenhum líder é corista).
     */
    static boolean ehCorista(Usuario usuario) {
        return usuario.getRoles().stream().anyMatch(role -> role.getNome().startsWith("CORISTA_"));
    }

    /**
     * O presbítero enxerga as duas listas, mas só da congregação em que está cadastrado. Vale quando o presbítero é o
     * único papel com recorte do usuário; qualquer papel de maior alcance (líder, pastor, admin) tira a restrição.
     */
    static Optional<UUID> congregacaoRestrita(Usuario usuario) {
        boolean soPresbitero = usuario.getRoles().stream()
                .map(Role::getNome)
                .filter(POR_PAPEL::containsKey)
                .collect(Collectors.toSet())
                .equals(Set.of("PRESBITERO"));
        if (!soPresbitero) {
            return Optional.empty();
        }
        return Optional.of(usuario.getPessoa().getCongregacao().getId());
    }

    /** União do recorte de todos os papéis do usuário; vazio se nenhum papel tem recorte. */
    static Set<ListaClassificacao> listasPermitidas(Usuario usuario) {
        Set<ListaClassificacao> listas = EnumSet.noneOf(ListaClassificacao.class);
        for (Role role : usuario.getRoles()) {
            listas.addAll(POR_PAPEL.getOrDefault(role.getNome(), Set.of()));
        }
        return listas;
    }
}
