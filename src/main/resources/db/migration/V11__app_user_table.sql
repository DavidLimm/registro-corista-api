CREATE TABLE app_user (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    pessoa_id     UUID         NOT NULL REFERENCES pessoa (id),
    email         VARCHAR(150) NOT NULL,
    senha_hash    VARCHAR(255) NOT NULL,
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_app_user_pessoa UNIQUE (pessoa_id),
    CONSTRAINT uk_app_user_email UNIQUE (email)
);

-- papéis são vínculos (RBAC): um app_user pode acumular mais de um role
CREATE TABLE app_user_role (
    app_user_id UUID NOT NULL REFERENCES app_user (id),
    role_id     UUID NOT NULL REFERENCES role (id),

    PRIMARY KEY (app_user_id, role_id)
);

-- FKs adiadas nas migrations de pessoa (V4) e corista (V6) até existir app_user
ALTER TABLE pessoa
    ADD CONSTRAINT fk_pessoa_aprovado_por FOREIGN KEY (aprovado_por) REFERENCES app_user (id);

ALTER TABLE corista
    ADD CONSTRAINT fk_corista_promovido_por FOREIGN KEY (promovido_por) REFERENCES app_user (id);
