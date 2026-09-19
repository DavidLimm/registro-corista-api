CREATE TABLE congregacao (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    area_id       UUID         NOT NULL REFERENCES area (id),
    endereco_id   UUID         REFERENCES endereco (id),
    nome          VARCHAR(150) NOT NULL,
    ativa         BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- também serve de índice para buscas por area_id
    CONSTRAINT uk_congregacao_area_nome UNIQUE (area_id, nome)
);
