-- Acrescenta o tamanho PP ao CHECK de tamanho_camisa (V8 já aplicada, não pode ser editada).
ALTER TABLE corista
    DROP CONSTRAINT ck_corista_tamanho_camisa,
    ADD CONSTRAINT ck_corista_tamanho_camisa
        CHECK (tamanho_camisa IN ('PP', 'P', 'M', 'G', 'GG', 'XGG'));
