package com.registraai.registro_coristas_api.role.service;

import com.registraai.registro_coristas_api.role.model.Permissao;
import com.registraai.registro_coristas_api.role.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PermissaoService {

    private final RoleRepository roleRepository;

    /**
     * Permissões concedidas a uma role, lidas do banco. Devolve conjunto vazio se a role não existir ou não tiver
     * permissões. Lança {@link IllegalStateException} se o banco tiver um nome que o enum {@link Permissao} não conhece:
     * isso é divergência entre migration e código, e precisa aparecer em vez de ser ignorado.
     */
    @Transactional(readOnly = true)
    public Set<Permissao> de(String nomeDaRole) {
        Set<Permissao> permissoes = EnumSet.noneOf(Permissao.class);
        for (String nome : roleRepository.findNomesDePermissoesPorNomeDaRole(nomeDaRole)) {
            permissoes.add(converter(nome));
        }
        return permissoes;
    }

    private Permissao converter(String nome) {
        try {
            return Permissao.valueOf(nome);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Permissão '" + nome + "' existe no banco mas não no enum Permissao", e);
        }
    }
}
