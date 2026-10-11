package com.registraai.registro_coristas_api.autenticacao.security;

import com.registraai.registro_coristas_api.usuario.model.Usuario;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;

/**
 * Roda <b>depois</b> de a senha conferir (é o {@code postAuthenticationChecks} do provider). Por padrão o Spring
 * checa "conta desabilitada" antes da senha, o que deixaria qualquer um descobrir, sem saber a senha, que um e-mail
 * existe e em que situação está. Aqui só quem acertou a senha vê o motivo do bloqueio.
 */
public class VerificadorDeAcesso implements UserDetailsChecker {

    @Override
    public void check(UserDetails userDetails) {
        if (userDetails.isEnabled()) {
            return;
        }
        throw new DisabledException(motivo((Usuario) userDetails));
    }

    private String motivo(Usuario usuario) {
        if (!usuario.isAtivo()) {
            return "Seu acesso foi desativado. Procure o líder da mocidade.";
        }
        return switch (usuario.getPessoa().getStatus()) {
            case PENDENTE -> "Seu cadastro está pendente de aprovação.";
            case REPROVADO -> "Seu cadastro não foi aprovado. Procure o líder da mocidade.";
            case AFASTADO -> "Você está afastado e não tem acesso ao sistema no momento.";
            case INATIVO -> "Seu cadastro está inativo.";
            case APROVADO -> "Seu acesso está indisponível.";
        };
    }
}
