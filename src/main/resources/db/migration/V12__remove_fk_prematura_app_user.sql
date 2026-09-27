-- A V11 adiantou as FKs de pessoa.aprovado_por e corista.promovido_por para app_user, mas isso foi prematuro:
-- ainda não existe endpoint de aprovação/promoção (depende de autenticação), então hoje esses campos só são
-- preenchidos "manualmente" (inclusive nos testes) com um UUID que não corresponde a nenhum app_user real.
-- Reverte para coluna solta, sem FK; a constraint volta quando os endpoints reais existirem.
ALTER TABLE pessoa
    DROP CONSTRAINT fk_pessoa_aprovado_por;

ALTER TABLE corista
    DROP CONSTRAINT fk_corista_promovido_por;
