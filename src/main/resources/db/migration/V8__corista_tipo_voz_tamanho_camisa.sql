-- tipo_voz e tamanho_camisa passam a ser obrigatórios e restritos aos valores dos enums TipoVoz e TamanhoCamisa.
-- A V6 (já aplicada) os criou como VARCHAR nulos e sem CHECK. Falha se já houver corista com esses campos nulos.
ALTER TABLE corista
    ALTER COLUMN tipo_voz       TYPE VARCHAR(20),
    ALTER COLUMN tipo_voz       SET NOT NULL,
    ALTER COLUMN tamanho_camisa TYPE VARCHAR(20),
    ALTER COLUMN tamanho_camisa SET NOT NULL,
    ADD CONSTRAINT ck_corista_tipo_voz
        CHECK (tipo_voz IN ('BAIXO', 'CONTRALTO', 'SOPRANO', 'TENOR')),
    ADD CONSTRAINT ck_corista_tamanho_camisa
        CHECK (tamanho_camisa IN ('PP', 'P', 'M', 'G', 'GG', 'XGG'));
