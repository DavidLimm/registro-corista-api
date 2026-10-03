-- Renomeia app_user -> usuario (e app_user_role -> usuario_role) para manter a nomenclatura em português.
-- Não editamos a V11 porque ela já foi aplicada: o histórico do Flyway precisa permanecer imutável.
ALTER TABLE app_user RENAME TO usuario;
ALTER TABLE app_user_role RENAME TO usuario_role;
ALTER TABLE usuario_role RENAME COLUMN app_user_id TO usuario_id;

ALTER TABLE usuario RENAME CONSTRAINT app_user_pkey TO usuario_pkey;
ALTER TABLE usuario RENAME CONSTRAINT uk_app_user_pessoa TO uk_usuario_pessoa;
ALTER TABLE usuario RENAME CONSTRAINT uk_app_user_email TO uk_usuario_email;
ALTER TABLE usuario RENAME CONSTRAINT app_user_pessoa_id_fkey TO usuario_pessoa_id_fkey;

ALTER TABLE usuario_role RENAME CONSTRAINT app_user_role_pkey TO usuario_role_pkey;
ALTER TABLE usuario_role RENAME CONSTRAINT app_user_role_app_user_id_fkey TO usuario_role_usuario_id_fkey;
ALTER TABLE usuario_role RENAME CONSTRAINT app_user_role_role_id_fkey TO usuario_role_role_id_fkey;
