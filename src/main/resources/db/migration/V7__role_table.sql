CREATE TABLE role (
    id        UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    nome      VARCHAR(50)  NOT NULL,
    descricao VARCHAR(255) NOT NULL,

    CONSTRAINT uk_role_nome UNIQUE (nome)
);

INSERT INTO role (nome, descricao) VALUES
    ('ADMIN',                'Acesso global a todas as áreas'),
    ('PASTOR',               'Leitura geral da(s) área(s) designada(s), até 2 áreas'),
    ('PRESBITERO',           'Gestão no escopo da congregação; aprova cadastros'),
    ('LIDER_MOCIDADE',       'Líder da mocidade; promove adolescentes para a lista de jovens'),
    ('LIDER_JOVENS',         'Líder dos jovens'),
    ('LIDER_ADOLESCENTES',   'Líder dos adolescentes'),
    ('APOIO_ADOLESCENTES',   'Apoio operacional dos adolescentes'),
    ('APOIO_JOVENS',         'Apoio operacional dos jovens'),
    ('MAESTRO_ADOLESCENTES', 'Maestro do recorte de adolescentes'),
    ('MAESTRO_JOVENS',       'Maestro do recorte de jovens'),
    ('CORISTA_ADOLESCENTES', 'Corista da lista de adolescentes'),
    ('CORISTA_JOVENS',       'Corista da lista de jovens');
