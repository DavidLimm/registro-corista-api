package com.registraai.registro_coristas_api.role.service;

import com.registraai.registro_coristas_api.role.model.Permissao;
import com.registraai.registro_coristas_api.role.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PermissaoServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private PermissaoService permissaoService;

    @Test
    void de_convertePermissoesDoBancoParaOEnum() {
        given(roleRepository.findNomesDePermissoesPorNomeDaRole("CORISTA_JOVENS"))
                .willReturn(List.of("CORISTA_CRIAR", "CORISTA_LER"));

        assertThat(permissaoService.de("CORISTA_JOVENS"))
                .containsExactlyInAnyOrder(Permissao.CORISTA_CRIAR, Permissao.CORISTA_LER);
    }

    @Test
    void de_roleSemPermissoes_devolveConjuntoVazio() {
        given(roleRepository.findNomesDePermissoesPorNomeDaRole("ROLE_INEXISTENTE")).willReturn(List.of());

        assertThat(permissaoService.de("ROLE_INEXISTENTE")).isEmpty();
    }

    @Test
    void de_nomeQueNaoExisteNoEnum_lancaExcecaoEmVezDeIgnorar() {
        given(roleRepository.findNomesDePermissoesPorNomeDaRole("PASTOR"))
                .willReturn(List.of("PERMISSAO_FANTASMA"));

        assertThatThrownBy(() -> permissaoService.de("PASTOR"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PERMISSAO_FANTASMA");
    }
}
