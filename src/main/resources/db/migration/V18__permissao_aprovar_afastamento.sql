-- Quem aprova (e reverte) o afastamento de um corista é só o LIDER_MOCIDADE e o PASTOR (e o ADMIN, que tem tudo).
-- Os demais papéis só solicitam, via CORISTA_SOLICITAR_AFASTAMENTO.
INSERT INTO permissao (nome, descricao) VALUES
    ('CORISTA_APROVAR_AFASTAMENTO', 'Aprovar ou reverter o afastamento de um corista');

INSERT INTO role_permissao (role_id, permissao_id)
SELECT r.id, p.id FROM role r CROSS JOIN permissao p
WHERE r.nome IN ('LIDER_MOCIDADE', 'PASTOR', 'ADMIN')
  AND p.nome = 'CORISTA_APROVAR_AFASTAMENTO';
