-- Corista só enxerga o próprio cadastro. A leitura geral de coristas (CORISTA_LER) fica com a liderança, apoio e
-- maestros; o acesso ao próprio cadastro não é permissão, é regra do Service/@PreAuthorize (ehProprio).
DELETE FROM role_permissao
WHERE role_id IN (SELECT id FROM role WHERE nome IN ('CORISTA_ADOLESCENTES', 'CORISTA_JOVENS'))
  AND permissao_id = (SELECT id FROM permissao WHERE nome = 'CORISTA_LER');
