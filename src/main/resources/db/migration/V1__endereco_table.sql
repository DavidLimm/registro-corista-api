CREATE TABLE endereco (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    logradouro    VARCHAR(150) NOT NULL,
    numero        VARCHAR(10),
    complemento   VARCHAR(100),
    bairro        VARCHAR(100) NOT NULL,
    cidade        VARCHAR(100) NOT NULL,
    uf            VARCHAR(2)   NOT NULL,
    cep           VARCHAR(8),
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_endereco_uf  CHECK (uf ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_endereco_cep CHECK (cep IS NULL OR cep ~ '^[0-9]{8}$')
);
