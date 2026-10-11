package com.registraai.registro_coristas_api.config;

import com.registraai.registro_coristas_api.autenticacao.filter.JwtAutenticacaoFilter;
import com.registraai.registro_coristas_api.autenticacao.security.ProblemDetailSeguranca;
import com.registraai.registro_coristas_api.autenticacao.security.VerificadorDeAcesso;
import com.registraai.registro_coristas_api.autenticacao.service.TokenService;
import com.registraai.registro_coristas_api.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SegurancaConfig {

    private final UsuarioRepository usuarioRepository;

    @Bean
    public UserDetailsService userDetailsService() {
        return email -> usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService());
        provider.setPasswordEncoder(passwordEncoder());
        // "conta liberada?" só depois de a senha conferir (ver VerificadorDeAcesso)
        provider.setPreAuthenticationChecks(usuario -> { });
        provider.setPostAuthenticationChecks(new VerificadorDeAcesso());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, TokenService tokenService,
                                                   ProblemDetailSeguranca respostasDeErro) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(erros -> erros
                        .authenticationEntryPoint(respostasDeErro)
                        .accessDeniedHandler(respostasDeErro))
                .authorizeHttpRequests(req -> req
                        .requestMatchers("/v1/api/auth/login", "/v1/api/auth/redefinicao-senha/**").permitAll()
                        // auto-cadastro de corista (nasce PENDENTE; o login só vale depois de APROVADO)
                        .requestMatchers(HttpMethod.POST, "/v1/api/coristas").permitAll()
                        // o formulário de auto-cadastro lista as congregações antes do login (só leitura)
                        .requestMatchers(HttpMethod.GET, "/v1/api/congregacoes", "/v1/api/congregacoes/*").permitAll()
                        // idem para a hierarquia (agrupamentos e suas subdivisões): o formulário escolhe tipo → agrupamento → subdivisão
                        .requestMatchers(HttpMethod.GET, "/v1/api/agrupamentos", "/v1/api/agrupamentos/**").permitAll()
                        // em prod o Swagger/OpenAPI está desabilitado no application-prod.yaml (responde 404)
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // sem isso, qualquer erro tratado pelo container vira 401 no lugar do status real
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAutenticacaoFilter(tokenService, userDetailsService()),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

}
