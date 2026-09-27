package com.registraai.registro_coristas_api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SegurancaConfig {

    /**
     * Só o hash da senha, sem autenticação/login (ainda não implementados — ver AGENTS.md). Usado ao gravar
     * {@code AppUser.senhaHash}; nunca gravar senha em texto puro.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
