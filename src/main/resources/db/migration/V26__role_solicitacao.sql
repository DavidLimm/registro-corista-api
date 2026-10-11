-- Fluxo de papéis: quem tem ROLE_SOLICITAR (ex.: LIDER_MOCIDADE) pede um papel novo, que nasce PENDENTE e inativo; o
-- PASTOR (ROLE_APROVAR) aprova (APROVADA, ativo) ou reprova (REPROVADA). Os papéis que já existiam são APROVADA.
-- As permissões do papel novo são dadas depois, por quem tem ROLE_GERENCIAR_PERMISSOES.
ALTER TABLE role
    ADD COLUMN status         VARCHAR(20) NOT NULL DEFAULT 'APROVADA',
    ADD COLUMN solicitado_por UUID,
    ADD COLUMN decidido_por   UUID,
    ADD COLUMN decidido_em    TIMESTAMPTZ,
    ADD CONSTRAINT ck_role_status CHECK (status IN ('PENDENTE', 'APROVADA', 'REPROVADA')),
    ADD CONSTRAINT ck_role_decisao_junta CHECK ((decidido_por IS NULL) = (decidido_em IS NULL));
