-- Permissões passam a ser dados: o PASTOR pode alterar quais permissões cada role recebe, sem deploy.
-- O catálogo de ações (enum Permissao) continua no código, e os nomes aqui precisam bater com ele.
-- O estado inicial abaixo reproduz exatamente o mapa de PermissoesPorRole.
CREATE TABLE permissao (
    id        UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    nome      VARCHAR(60)  NOT NULL,
    descricao VARCHAR(255) NOT NULL,

    CONSTRAINT uk_permissao_nome UNIQUE (nome)
);

CREATE TABLE role_permissao (
    role_id      UUID NOT NULL REFERENCES role (id),
    permissao_id UUID NOT NULL REFERENCES permissao (id),

    PRIMARY KEY (role_id, permissao_id)
);

INSERT INTO permissao (nome, descricao) VALUES
    ('PESSOA_CRIAR',                  'Cadastrar nova pessoa'),
    ('PESSOA_LER',                    'Consultar pessoas'),
    ('PESSOA_ATUALIZAR',              'Editar dados de uma pessoa'),
    ('PESSOA_INATIVAR',               'Remover (soft delete) uma pessoa'),
    ('PESSOA_APROVAR',                'Aprovar cadastro pendente de pessoa'),
    ('PESSOA_REPROVAR',               'Reprovar cadastro pendente de pessoa'),
    ('CORISTA_CRIAR',                 'Cadastrar corista'),
    ('CORISTA_LER',                   'Consultar coristas'),
    ('CORISTA_ATUALIZAR',             'Editar dados de corista'),
    ('CORISTA_ATUALIZAR_VOZ',         'Editar apenas o tipo de voz do corista'),
    ('CORISTA_INATIVAR',              'Remover (soft delete) um corista'),
    ('CORISTA_APROVAR',               'Aprovar cadastro de corista'),
    ('CORISTA_REPROVAR',              'Reprovar cadastro de corista'),
    ('CORISTA_PROMOVER',              'Promover corista de adolescente para jovem (irreversível)'),
    ('CORISTA_SOLICITAR_AFASTAMENTO', 'Solicitar afastamento de um corista'),
    ('AREA_GERENCIAR',                'Criar, editar e inativar áreas'),
    ('CONGREGACAO_GERENCIAR',         'Criar, editar e inativar congregações'),
    ('USUARIO_GERENCIAR',             'Criar, editar, inativar e reativar logins'),
    ('ROLE_SOLICITAR',                'Solicitar criação de uma role nova'),
    ('ROLE_APROVAR',                  'Aprovar ou reprovar role solicitada'),
    ('ROLE_GERENCIAR_PERMISSOES',     'Alterar as permissões concedidas a cada role'),
    ('DASHBOARD_LER',                 'Acessar dashboards');

-- Corista e apoio: criar, ler e atualizar (o escopo "próprio" e o recorte ficam no Service)
INSERT INTO role_permissao (role_id, permissao_id)
SELECT r.id, p.id FROM role r CROSS JOIN permissao p
WHERE r.nome IN ('CORISTA_ADOLESCENTES', 'CORISTA_JOVENS', 'APOIO_ADOLESCENTES', 'APOIO_JOVENS')
  AND p.nome IN ('CORISTA_CRIAR', 'CORISTA_LER', 'CORISTA_ATUALIZAR');

-- Maestro: criar, ler e trocar apenas a voz
INSERT INTO role_permissao (role_id, permissao_id)
SELECT r.id, p.id FROM role r CROSS JOIN permissao p
WHERE r.nome IN ('MAESTRO_ADOLESCENTES', 'MAESTRO_JOVENS')
  AND p.nome IN ('CORISTA_CRIAR', 'CORISTA_LER', 'CORISTA_ATUALIZAR_VOZ');

-- Líder de adolescentes e de jovens: o do apoio + aprovar, reprovar e solicitar afastamento
INSERT INTO role_permissao (role_id, permissao_id)
SELECT r.id, p.id FROM role r CROSS JOIN permissao p
WHERE r.nome IN ('LIDER_ADOLESCENTES', 'LIDER_JOVENS')
  AND p.nome IN ('CORISTA_CRIAR', 'CORISTA_LER', 'CORISTA_ATUALIZAR',
                 'CORISTA_APROVAR', 'CORISTA_REPROVAR', 'CORISTA_SOLICITAR_AFASTAMENTO');

-- Dirigentes: como o líder, mas nunca reprovam
INSERT INTO role_permissao (role_id, permissao_id)
SELECT r.id, p.id FROM role r CROSS JOIN permissao p
WHERE r.nome IN ('DIRIGENTE_UNIAO', 'LIDERANCA_GRUPO_JOVEM', 'DIRIGENTE_CAMPANHA')
  AND p.nome IN ('CORISTA_CRIAR', 'CORISTA_LER', 'CORISTA_ATUALIZAR',
                 'CORISTA_APROVAR', 'CORISTA_SOLICITAR_AFASTAMENTO');

-- Líder da mocidade: semi-completo; não gere usuários nem papéis
INSERT INTO role_permissao (role_id, permissao_id)
SELECT r.id, p.id FROM role r CROSS JOIN permissao p
WHERE r.nome = 'LIDER_MOCIDADE'
  AND p.nome IN ('CORISTA_CRIAR', 'CORISTA_LER', 'CORISTA_ATUALIZAR',
                 'CORISTA_APROVAR', 'CORISTA_REPROVAR', 'CORISTA_SOLICITAR_AFASTAMENTO',
                 'PESSOA_CRIAR', 'PESSOA_LER', 'PESSOA_ATUALIZAR', 'PESSOA_INATIVAR',
                 'PESSOA_APROVAR', 'PESSOA_REPROVAR', 'CORISTA_INATIVAR', 'CORISTA_PROMOVER',
                 'ROLE_SOLICITAR');

-- Presbítero: leitura de coristas da congregação, dashboards e pedido de afastamento
INSERT INTO role_permissao (role_id, permissao_id)
SELECT r.id, p.id FROM role r CROSS JOIN permissao p
WHERE r.nome = 'PRESBITERO'
  AND p.nome IN ('DASHBOARD_LER', 'CORISTA_LER', 'CORISTA_SOLICITAR_AFASTAMENTO');

-- Pastor e admin: todas as permissões
INSERT INTO role_permissao (role_id, permissao_id)
SELECT r.id, p.id FROM role r CROSS JOIN permissao p
WHERE r.nome IN ('PASTOR', 'ADMIN');
