-- A data de nascimento decide a lista (adolescentes/jovens): o corista a informa no cadastro e não a altera mais.
-- Só os líderes corrigem (ex.: quem mentiu a idade para entrar nos jovens). PASTOR e ADMIN também.
INSERT INTO permissao (nome, descricao) VALUES
    ('CORISTA_CORRIGIR_NASCIMENTO', 'Corrigir a data de nascimento de um corista');

INSERT INTO role_permissao (role_id, permissao_id)
SELECT r.id, p.id FROM role r CROSS JOIN permissao p
WHERE r.nome IN ('LIDER_MOCIDADE', 'LIDER_JOVENS', 'LIDER_ADOLESCENTES', 'PASTOR', 'ADMIN')
  AND p.nome = 'CORISTA_CORRIGIR_NASCIMENTO';
