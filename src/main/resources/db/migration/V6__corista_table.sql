CREATE TABLE corista (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    pessoa_id           UUID         NOT NULL REFERENCES pessoa (id),
    tipo_voz            VARCHAR(30),
    tamanho_camisa      VARCHAR(10),
    ocupacao            VARCHAR(100),

    -- padrão segue a idade; faixa etária real é sempre derivada de pessoa.data_nascimento
    lista_classificacao VARCHAR(20)  NOT NULL,

    -- promoção antecipada (só ADOLESCENTE -> JOVEM, permanente). FK de promovido_por
    -- para app_user será adicionada na migration de app_user
    promovido_por       UUID,
    promovido_em        TIMESTAMPTZ,

    criado_em           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_corista_pessoa UNIQUE (pessoa_id),
    CONSTRAINT ck_corista_lista_classificacao CHECK (lista_classificacao IN ('ADOLESCENTE', 'JOVEM')),
    CONSTRAINT ck_corista_promocao_completa
        CHECK ((promovido_por IS NULL) = (promovido_em IS NULL)),
    CONSTRAINT ck_corista_promocao_somente_para_jovem
        CHECK (promovido_em IS NULL OR lista_classificacao = 'JOVEM')
);

CREATE INDEX idx_corista_lista_classificacao ON corista (lista_classificacao);
