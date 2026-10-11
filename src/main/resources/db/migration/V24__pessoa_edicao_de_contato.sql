-- Quando a própria pessoa mexeu pela última vez no telefone e no endereço dela. Serve ao limite de uma alteração a
-- cada 10 dias (quem edita a ficha de outra pessoa, como a liderança, não é limitado nem registra aqui).
ALTER TABLE pessoa
    ADD COLUMN telefone_editado_em TIMESTAMPTZ,
    ADD COLUMN endereco_editado_em TIMESTAMPTZ;
