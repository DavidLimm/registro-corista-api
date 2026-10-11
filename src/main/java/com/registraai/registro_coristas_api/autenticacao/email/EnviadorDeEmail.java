package com.registraai.registro_coristas_api.autenticacao.email;

/**
 * Porta de saída dos e-mails do sistema. A implementação real (SMTP ou um provedor) ainda não existe: em dev o
 * token vai para o log ({@link EnviadorDeEmailEmLog}); em prod nada é enviado até haver uma implementação.
 */
public interface EnviadorDeEmail {

    /** {@code token} é o valor em texto puro que o usuário usa na tela de redefinição (nunca é gravado no banco). */
    void enviarRedefinicaoDeSenha(String destinatario, String token);
}
