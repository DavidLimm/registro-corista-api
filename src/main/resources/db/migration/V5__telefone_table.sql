CREATE TABLE telefone (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    pessoa_id     UUID        NOT NULL REFERENCES pessoa (id),
    numero        VARCHAR(20) NOT NULL,
    whatsapp      BOOLEAN     NOT NULL DEFAULT FALSE,
    principal     BOOLEAN     NOT NULL DEFAULT FALSE,
    criado_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- também serve de índice para buscas por pessoa_id
    CONSTRAINT uk_telefone_pessoa_numero UNIQUE (pessoa_id, numero)
);

-- no máximo um telefone principal por pessoa
CREATE UNIQUE INDEX uk_telefone_principal_por_pessoa ON telefone (pessoa_id) WHERE principal;
