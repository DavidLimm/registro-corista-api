-- Fecha a migração para a hierarquia agrupamento -> subdivisao -> congregacao (V28 foi aditiva).
-- A congregação passa a pertencer só a uma subdivisão; a tabela `area` e a permissão AREA_GERENCIAR saem.

ALTER TABLE congregacao DROP CONSTRAINT uk_congregacao_area_nome;
ALTER TABLE congregacao DROP COLUMN area_id;

ALTER TABLE congregacao ALTER COLUMN subdivisao_id SET NOT NULL;

-- o nome da congregação é único dentro da subdivisão (também serve de índice por subdivisao_id)
ALTER TABLE congregacao ADD CONSTRAINT uk_congregacao_subdivisao_nome UNIQUE (subdivisao_id, nome);
DROP INDEX idx_congregacao_subdivisao;

DROP TABLE area;

DELETE FROM role_permissao
WHERE permissao_id = (SELECT id FROM permissao WHERE nome = 'AREA_GERENCIAR');
DELETE FROM permissao WHERE nome = 'AREA_GERENCIAR';
