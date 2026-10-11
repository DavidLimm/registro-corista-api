package com.registraai.registro_coristas_api.suporte;

import com.registraai.registro_coristas_api.autenticacao.security.ProblemDetailSeguranca;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Segurança dos testes de controller ({@code @WebMvcTest} não carrega a {@code SegurancaConfig}): tudo exige
 * autenticação, o CSRF fica desligado e o {@code @PreAuthorize} dos controllers vale, com as mesmas respostas
 * 401/403 em Problem Details da aplicação.
 */
@TestConfiguration
@EnableMethodSecurity
@Import(ProblemDetailSeguranca.class)
public class SegurancaTesteConfig {

    @Bean
    SecurityFilterChain filtrosDeSegurancaDeTeste(HttpSecurity http, ProblemDetailSeguranca respostasDeErro)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .exceptionHandling(erros -> erros
                        .authenticationEntryPoint(respostasDeErro)
                        .accessDeniedHandler(respostasDeErro))
                .authorizeHttpRequests(req -> req.anyRequest().authenticated());
        return http.build();
    }
}
