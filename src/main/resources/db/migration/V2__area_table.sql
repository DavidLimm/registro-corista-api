CREATE TABLE area (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    numero        INTEGER      NOT NULL,
    nome          VARCHAR(100) NOT NULL,
    ativa         BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_area_numero UNIQUE (numero),
    CONSTRAINT ck_area_numero_positivo CHECK (numero > 0)
);
