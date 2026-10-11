-- A partir daqui o escopo por agrupamento filtra as consultas. Quem já tinha login antes da V31 não tem vínculo, e
-- ficaria sem enxergar nada: recebe o agrupamento da congregação da própria pessoa (o mesmo que o auto-cadastro dá).
-- ADMIN e PASTOR_ADMIN são globais e não têm vínculo. Os pastores podem ser ajustados depois pelo ESCOPO_GERENCIAR.
INSERT INTO usuario_agrupamento (usuario_id, agrupamento_id)
SELECT u.id, s.agrupamento_id
FROM usuario u
JOIN pessoa p ON p.id = u.pessoa_id
JOIN congregacao c ON c.id = p.congregacao_id
JOIN subdivisao s ON s.id = c.subdivisao_id
WHERE NOT EXISTS (SELECT 1 FROM usuario_agrupamento ua WHERE ua.usuario_id = u.id)
  AND NOT EXISTS (SELECT 1
                  FROM usuario_role ur
                  JOIN role r ON r.id = ur.role_id
                  WHERE ur.usuario_id = u.id AND r.nome IN ('ADMIN', 'PASTOR_ADMIN'));
