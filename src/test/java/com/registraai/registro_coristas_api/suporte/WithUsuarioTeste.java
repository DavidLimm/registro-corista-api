package com.registraai.registro_coristas_api.suporte;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;

/**
 * Autentica o teste como um {@code Usuario} de verdade (não o {@code User} do {@code @WithMockUser}), porque os
 * controllers recebem {@code @AuthenticationPrincipal Usuario}. Sem argumentos: papel {@code ADMIN} com todas as
 * permissões. Para testar a autorização, informe {@code papeis} e {@code permissoes} (e {@code todasPermissoes = false}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({TYPE, METHOD})
@WithSecurityContext(factory = UsuarioTesteFactory.class)
public @interface WithUsuarioTeste {

    /** Id do usuário de teste: os controllers gravam esse id como quem aprovou/reprovou/afastou. */
    String ID_PADRAO = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    String[] papeis() default {"ADMIN"};

    boolean todasPermissoes() default true;

    String[] permissoes() default {};
}
