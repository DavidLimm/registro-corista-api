-- Escopo do usuário: o vínculo é com o agrupamento (substitui a ideia antiga de usuario_area).
-- A quantidade permitida por papel é regra do Service, não constraint de banco.
CREATE TABLE usuario_agrupamento (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id     UUID        NOT NULL REFERENCES usuario (id),
    agrupamento_id UUID        NOT NULL REFERENCES agrupamento (id),
    -- trilha: quem fez o vínculo (nulo no auto-cadastro, em que o vínculo vem da congregação escolhida); sem FK, como as
    -- demais colunas de trilha (aprovado_por, promovido_por...)
    vinculado_por  UUID,
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- também serve de índice por usuario_id
    CONSTRAINT uk_usuario_agrupamento UNIQUE (usuario_id, agrupamento_id)
);

CREATE INDEX idx_usuario_agrupamento_agrupamento ON usuario_agrupamento (agrupamento_id);

-- Quem vincula e desvincula usuários de agrupamentos.
INSERT INTO permissao (nome, descricao) VALUES
    ('ESCOPO_GERENCIAR', 'Vincular e desvincular usuários de agrupamentos (escopo de acesso)');

-- PASTOR_ADMIN: pastor geral. Acesso global, mas só de leitura nos dados de pessoas e coristas: gerencia a estrutura
-- (agrupamentos e subdivisões) e o escopo dos pastores, sem editar nada que o pastor da área faz.
INSERT INTO role (nome, descricao) VALUES
    ('PASTOR_ADMIN', 'Pastor geral: acesso global somente leitura aos dados; gerencia agrupamentos, subdivisões e o acesso dos pastores');

INSERT INTO role_permissao (role_id, permissao_id)
SELECT r.id, p.id FROM role r CROSS JOIN permissao p
WHERE r.nome = 'PASTOR_ADMIN'
  AND p.nome IN ('PESSOA_LER', 'CORISTA_LER', 'DASHBOARD_LER',
                 'AGRUPAMENTO_GERENCIAR', 'ESCOPO_GERENCIAR', 'USUARIO_GERENCIAR');

INSERT INTO role_permissao (role_id, permissao_id)
SELECT r.id, p.id FROM role r CROSS JOIN permissao p
WHERE r.nome = 'ADMIN'
  AND p.nome = 'ESCOPO_GERENCIAR';
