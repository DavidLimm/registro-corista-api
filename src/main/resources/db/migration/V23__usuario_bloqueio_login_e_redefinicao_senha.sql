-- Login: 3 senhas erradas seguidas bloqueiam a conta até a senha ser redefinida. senha_alterada_em invalida os JWT
-- emitidos antes da troca/redefinição (o filtro compara com o "iat" do token).
ALTER TABLE usuario
    ADD COLUMN tentativas_falhas  INT         NOT NULL DEFAULT 0,
    ADD COLUMN bloqueado_em       TIMESTAMPTZ,
    ADD COLUMN senha_alterada_em  TIMESTAMPTZ;

-- Redefinição de senha: só o hash SHA-256 do token fica no banco (o token em si vai por e-mail e nunca é gravado).
CREATE TABLE redefinicao_senha (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id  UUID         NOT NULL REFERENCES usuario (id),
    token_hash  VARCHAR(64)  NOT NULL,
    expira_em   TIMESTAMPTZ  NOT NULL,
    usado_em    TIMESTAMPTZ,
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_redefinicao_senha_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_redefinicao_senha_usuario_id ON redefinicao_senha (usuario_id);
