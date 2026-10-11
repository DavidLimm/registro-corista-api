package com.registraai.registro_coristas_api.role.model;

/**
 * Ações protegidas do sistema. Permissão é código (versionada no Git), não dado no banco.
 * O recorte por área/congregação e o "próprio registro" não são permissão: são checados no Service.
 */
public enum Permissao {

    // Pessoa (cadastro comum a todos)
    PESSOA_CRIAR,        // cadastrar nova pessoa
    PESSOA_LER,          // consultar pessoas (lista e detalhe)
    PESSOA_ATUALIZAR,    // editar dados de uma pessoa
    PESSOA_INATIVAR,     // soft delete de uma pessoa
    PESSOA_APROVAR,      // aprovar cadastro pendente (PENDENTE -> APROVADO)
    PESSOA_REPROVAR,     // reprovar cadastro pendente (PENDENTE -> REPROVADO)

    // Corista (especialização de pessoa)
    CORISTA_CRIAR,               // cadastrar corista
    CORISTA_LER,                 // consultar coristas
    CORISTA_ATUALIZAR,           // editar dados de corista (todos os campos)
    CORISTA_ATUALIZAR_VOZ,       // editar apenas o tipo de voz do corista
    CORISTA_INATIVAR,            // soft delete de corista
    CORISTA_APROVAR,             // aprovar cadastro de corista
    CORISTA_REPROVAR,            // reprovar cadastro de corista (a checagem de faixa etária é regra do Service)
    CORISTA_PROMOVER,            // promoção antecipada ADOLESCENTE -> JOVEM (irreversível)
    CORISTA_SOLICITAR_AFASTAMENTO, // pedir afastamento de um corista
    CORISTA_APROVAR_AFASTAMENTO,   // aprovar ou reverter o afastamento (apenas LIDER_MOCIDADE e PASTOR)
    CORISTA_CORRIGIR_NASCIMENTO,   // corrigir a data de nascimento de um corista (líderes); o próprio corista não altera

    // Estrutura da igreja
    AREA_GERENCIAR,          // criar, editar e inativar áreas (apenas PASTOR)
    CONGREGACAO_GERENCIAR,   // criar, editar e inativar congregações (apenas PASTOR)

    // Acesso e papéis
    USUARIO_GERENCIAR,           // criar, editar, inativar e reativar logins (apenas PASTOR)
    ROLE_SOLICITAR,              // pedir criação de uma role nova (fica PENDENTE até o PASTOR aprovar)
    ROLE_APROVAR,                // aprovar ou reprovar role solicitada (apenas PASTOR)
    ROLE_GERENCIAR_PERMISSOES,   // alterar as permissões concedidas a cada role (apenas PASTOR)

    // Consultas gerenciais
    DASHBOARD_LER        // acessar dashboards
}
