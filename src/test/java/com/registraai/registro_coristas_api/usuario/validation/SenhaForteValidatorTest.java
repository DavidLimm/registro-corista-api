package com.registraai.registro_coristas_api.usuario.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SenhaForteValidatorTest {

    private final SenhaForteValidator validator = new SenhaForteValidator();

    @ParameterizedTest
    @ValueSource(strings = {"SenhaForte@123", "Aa1!aaaa", "Zz9#zzzzzzzzzzzz", "Ação#2026a"})
    void senhasQueAtendemATodosOsRequisitos(String senha) {
        assertThat(validator.isValid(senha, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Aa1!aaa",          // 7 caracteres
            "senhaforte@123",   // sem maiúscula
            "SENHAFORTE@123",   // sem minúscula
            "SenhaForte@abc",   // sem número
            "SenhaForte1234",   // sem caractere especial
            "Senha Forte 123",  // espaço não conta como caractere especial
            "        "})
    void senhasQueFaltamRequisitos(String senha) {
        assertThat(validator.isValid(senha, null)).isFalse();
    }

    @Test
    void nuloEAceito_aObrigatoriedadeENaOutraAnotacao() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
