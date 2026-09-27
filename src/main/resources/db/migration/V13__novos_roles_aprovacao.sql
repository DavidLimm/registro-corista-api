-- A aprovação/reprovação de cadastro deixa de ser exclusividade do PRESBITERO e passa para os líderes de cada
-- recorte: DIRIGENTE_UNIAO (adolescentes), LIDERANCA_GRUPO_JOVEM e DIRIGENTE_CAMPANHA (jovens) — além dos papéis
-- de liderança que já existiam (LIDER_MOCIDADE/LIDER_ADOLESCENTES/LIDER_JOVENS).
INSERT INTO role (nome, descricao) VALUES
    ('DIRIGENTE_UNIAO',       'Dirigente da União; aprova ou reprova cadastro de adolescentes da congregação'),
    ('LIDERANCA_GRUPO_JOVEM', 'Liderança do grupo de jovens; aprova ou reprova cadastro de jovens'),
    ('DIRIGENTE_CAMPANHA',    'Dirigente de campanha; aprova ou reprova cadastro de jovens');

UPDATE role SET descricao = 'Gestão no escopo da congregação; acesso a dashboards' WHERE nome = 'PRESBITERO';
