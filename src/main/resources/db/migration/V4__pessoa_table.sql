CREATE TABLE pessoa (
    id                         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    congregacao_id             UUID         NOT NULL REFERENCES congregacao (id),
    endereco_id                UUID         REFERENCES endereco (id),
    nome                       VARCHAR(150) NOT NULL,
    data_nascimento            DATE         NOT NULL,
    status                     VARCHAR(20)  NOT NULL DEFAULT 'PENDENTE',

    -- LGPD: responsável legal (obrigatório para menores; exigência validada no Service)
    responsavel_legal_nome     VARCHAR(150),
    responsavel_legal_telefone VARCHAR(20),
    consentimento_lgpd_em      TIMESTAMPTZ,

    -- trilha de aprovação (FK para app_user será adicionada na migration de app_user)
    aprovado_por               UUID,
    aprovado_em                TIMESTAMPTZ,

    criado_em                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em              TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_pessoa_status CHECK (status IN ('PENDENTE', 'APROVADO', 'INATIVO')),
    CONSTRAINT ck_pessoa_responsavel_legal_completo
        CHECK ((responsavel_legal_nome IS NULL) = (responsavel_legal_telefone IS NULL)),
    CONSTRAINT ck_pessoa_aprovacao_completa
        CHECK ((aprovado_por IS NULL) = (aprovado_em IS NULL))
);

CREATE INDEX idx_pessoa_congregacao_id ON pessoa (congregacao_id);
CREATE INDEX idx_pessoa_status         ON pessoa (status);
