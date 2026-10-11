-- Corista desligado: o cadastro de corista fica desativado (não é apagado) e sem acesso. Hoje o único motivo é
-- VIROU_LIDER: quem vira líder deixa de ser corista, mas continua sendo a mesma pessoa (status da pessoa não muda).
-- Quem sai ou passa da idade continua sendo INATIVO na própria pessoa, como antes.
ALTER TABLE corista
    ADD COLUMN desligado_em        TIMESTAMPTZ,
    ADD COLUMN motivo_desligamento VARCHAR(30),
    ADD CONSTRAINT ck_corista_desligamento_junto
        CHECK ((desligado_em IS NULL) = (motivo_desligamento IS NULL)),
    ADD CONSTRAINT ck_corista_motivo_desligamento
        CHECK (motivo_desligamento IS NULL OR motivo_desligamento IN ('VIROU_LIDER'));
