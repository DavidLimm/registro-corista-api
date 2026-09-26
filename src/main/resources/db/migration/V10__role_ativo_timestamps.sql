-- A V7 criou role sem soft delete/trilha de auditoria. Alinha com o padrão de area/congregacao.
ALTER TABLE role
    ADD COLUMN ativo         BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN criado_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now();
