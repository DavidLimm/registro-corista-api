package com.registraai.registro_coristas_api.usuario.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SenhaForteValidator implements ConstraintValidator<SenhaForte, String> {

    private static final int TAMANHO_MINIMO = 8;

    @Override
    public boolean isValid(String senha, ConstraintValidatorContext contexto) {
        if (senha == null) {
            return true;
        }
        return atende(senha);
    }

    /** Também usada fora do Bean Validation (ex.: testes e regras de serviço). */
    public static boolean atende(String senha) {
        if (senha.length() < TAMANHO_MINIMO) {
            return false;
        }
        boolean minuscula = false;
        boolean maiuscula = false;
        boolean numero = false;
        boolean especial = false;
        for (char c : senha.toCharArray()) {
            if (Character.isLowerCase(c)) {
                minuscula = true;
            } else if (Character.isUpperCase(c)) {
                maiuscula = true;
            } else if (Character.isDigit(c)) {
                numero = true;
            } else if (!Character.isWhitespace(c)) {
                especial = true;
            }
        }
        return minuscula && maiuscula && numero && especial;
    }
}
