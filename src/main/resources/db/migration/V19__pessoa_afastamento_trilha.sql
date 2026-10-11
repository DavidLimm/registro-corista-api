-- Trilha do afastamento (APROVADO <-> AFASTADO). Só existe enquanto o cadastro estiver AFASTADO: ao retornar,
-- o service limpa estas colunas, então a constraint abaixo garante que trilha e status nunca divergem.
ALTER TABLE pessoa
    ADD COLUMN afastado_por UUID,
    ADD COLUMN afastado_em  TIMESTAMPTZ;

ALTER TABLE pessoa
    ADD CONSTRAINT ck_pessoa_afastamento_completo
        CHECK ((afastado_por IS NULL) = (afastado_em IS NULL)),
    ADD CONSTRAINT ck_pessoa_afastamento_somente_se_afastado
        CHECK (afastado_em IS NULL OR status = 'AFASTADO');
