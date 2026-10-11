-- Afastamento temporário de um cadastro aprovado. Segue o mesmo padrão da V14: troca a constraint inteira.
ALTER TABLE pessoa
    DROP CONSTRAINT ck_pessoa_status;

ALTER TABLE pessoa
    ADD CONSTRAINT ck_pessoa_status CHECK (status IN ('PENDENTE', 'APROVADO', 'REPROVADO', 'AFASTADO', 'INATIVO'));
