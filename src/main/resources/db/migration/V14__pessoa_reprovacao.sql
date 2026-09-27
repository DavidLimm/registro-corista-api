-- Reprovação é um status próprio, distinto de INATIVO (soft delete): um cadastro reprovado nunca foi aprovado,
-- enquanto INATIVO é usado para remoção de um cadastro que já existia (aprovado ou não).
ALTER TABLE pessoa
    ADD COLUMN reprovado_por UUID,
    ADD COLUMN reprovado_em  TIMESTAMPTZ;

ALTER TABLE pessoa
    DROP CONSTRAINT ck_pessoa_status;

ALTER TABLE pessoa
    ADD CONSTRAINT ck_pessoa_status CHECK (status IN ('PENDENTE', 'APROVADO', 'REPROVADO', 'INATIVO')),
    ADD CONSTRAINT ck_pessoa_reprovacao_completa
        CHECK ((reprovado_por IS NULL) = (reprovado_em IS NULL));
