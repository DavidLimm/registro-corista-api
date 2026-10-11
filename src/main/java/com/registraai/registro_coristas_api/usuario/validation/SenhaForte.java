package com.registraai.registro_coristas_api.usuario.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;

/**
 * Senha com no mínimo 8 caracteres, contendo letra minúscula, letra maiúscula, número e caractere especial. Valor nulo
 * é aceito: a obrigatoriedade é de {@code @NotBlank}/{@code @NotNull}.
 */
@Documented
@Constraint(validatedBy = SenhaForteValidator.class)
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface SenhaForte {

    String message() default "A senha deve ter no mínimo 8 caracteres, com letra maiúscula, letra minúscula, número e caractere especial";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
