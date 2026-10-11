package com.registraai.registro_coristas_api.usuario.event;

import java.util.UUID;

/**
 * Publicado quando um usuário passa a ter algum papel que não é de corista (liderança, apoio, maestro...). Quem
 * precisar reagir (ex.: fechar o cadastro de corista da pessoa) escuta este evento, na mesma transação.
 */
public record PessoaVirouLiderEvent(UUID pessoaId) {
}
