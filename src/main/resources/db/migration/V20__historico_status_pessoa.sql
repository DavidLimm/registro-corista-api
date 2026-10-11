-- Histórico de todas as transições de status de uma pessoa (quem fez, de qual status para qual, e quando).
-- Complementa as colunas de trilha de pessoa (aprovado_por, reprovado_por, afastado_por): estas mostram só o último
-- evento, enquanto esta tabela guarda todos. Não é atualizada nem apagada: cada linha é um fato histórico.
CREATE TABLE historico_status_pessoa (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    pessoa_id       UUID         NOT NULL REFERENCES pessoa (id),
    status_anterior VARCHAR(20)  NOT NULL,
    status_novo     VARCHAR(20)  NOT NULL,
    realizado_por   UUID,
    realizado_em    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_historico_status_anterior CHECK (status_anterior IN ('PENDENTE', 'APROVADO', 'REPROVADO', 'AFASTADO', 'INATIVO')),
    CONSTRAINT ck_historico_status_novo CHECK (status_novo IN ('PENDENTE', 'APROVADO', 'REPROVADO', 'AFASTADO', 'INATIVO'))
);

CREATE INDEX idx_historico_status_pessoa_id ON historico_status_pessoa (pessoa_id);
