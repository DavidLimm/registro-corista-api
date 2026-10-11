-- Quem só pode solicitar o afastamento (CORISTA_SOLICITAR_AFASTAMENTO) registra o pedido aqui; quem aprova
-- (CORISTA_APROVAR_AFASTAMENTO: LIDER_MOCIDADE e PASTOR) aprova, e então a pessoa passa a AFASTADO, ou nega.
CREATE TABLE solicitacao_afastamento (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    pessoa_id       UUID          NOT NULL REFERENCES pessoa (id),
    motivo          VARCHAR(500)  NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDENTE',
    solicitado_por  UUID          NOT NULL,
    solicitado_em   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    decidido_por    UUID,
    decidido_em     TIMESTAMPTZ,

    CONSTRAINT ck_solicitacao_afastamento_status CHECK (status IN ('PENDENTE', 'APROVADA', 'NEGADA')),
    CONSTRAINT ck_solicitacao_afastamento_decisao CHECK ((decidido_por IS NULL) = (decidido_em IS NULL))
);

-- uma única solicitação pendente por pessoa
CREATE UNIQUE INDEX uk_solicitacao_afastamento_pendente ON solicitacao_afastamento (pessoa_id) WHERE status = 'PENDENTE';
CREATE INDEX idx_solicitacao_afastamento_status ON solicitacao_afastamento (status, solicitado_em);
