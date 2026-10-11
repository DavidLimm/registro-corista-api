-- Quem cria, edita e inativa agrupamentos (campo missionário, filial, setor, área) e suas subdivisões.
-- Por ora só o ADMIN; a AREA_GERENCIAR antiga continua até o pacote `area` ser removido.
INSERT INTO permissao (nome, descricao) VALUES
    ('AGRUPAMENTO_GERENCIAR', 'Criar, editar e inativar agrupamentos e suas subdivisões');

INSERT INTO role_permissao (role_id, permissao_id)
SELECT r.id, p.id FROM role r CROSS JOIN permissao p
WHERE r.nome = 'ADMIN'
  AND p.nome = 'AGRUPAMENTO_GERENCIAR';
