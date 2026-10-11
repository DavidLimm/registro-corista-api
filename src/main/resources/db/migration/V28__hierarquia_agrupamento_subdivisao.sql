-- Hierarquia organizacional: agrupamento (tipo) -> subdivisao -> congregacao.
-- Etapa aditiva: a tabela `area` e `congregacao.area_id` continuam até o código parar de usá-los.

CREATE TABLE agrupamento (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo          VARCHAR(30)  NOT NULL,
    nome          VARCHAR(150),
    numero        INTEGER,
    pais          VARCHAR(100),
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_agrupamento_tipo CHECK (tipo IN ('CAMPO_MISSIONARIO', 'FILIAL', 'SETOR', 'AREA')),
    CONSTRAINT ck_agrupamento_numero_positivo CHECK (numero IS NULL OR numero > 0),
    -- campos obrigatórios por tipo (a regra principal fica no Service)
    CONSTRAINT ck_agrupamento_campos_por_tipo CHECK (
        (tipo = 'CAMPO_MISSIONARIO' AND nome IS NOT NULL AND pais IS NOT NULL AND numero IS NULL)
        OR (tipo = 'FILIAL' AND nome IS NOT NULL AND pais IS NULL AND numero IS NULL)
        OR (tipo = 'SETOR' AND nome IS NOT NULL AND numero IS NOT NULL AND pais IS NULL)
        OR (tipo = 'AREA' AND numero IS NOT NULL AND nome IS NULL AND pais IS NULL)
    )
);

-- número de setor único entre setores; número de agrupamento AREA único entre os AREA
CREATE UNIQUE INDEX uk_agrupamento_setor_numero ON agrupamento (numero) WHERE tipo = 'SETOR';
CREATE UNIQUE INDEX uk_agrupamento_area_numero ON agrupamento (numero) WHERE tipo = 'AREA';
CREATE UNIQUE INDEX uk_agrupamento_campo_filial_nome ON agrupamento (tipo, lower(nome))
    WHERE tipo IN ('CAMPO_MISSIONARIO', 'FILIAL');

CREATE TABLE subdivisao (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    agrupamento_id UUID         NOT NULL REFERENCES agrupamento (id),
    numero         INTEGER,
    nome           VARCHAR(150),
    ativo          BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_subdivisao_numero_positivo CHECK (numero IS NULL OR numero > 0),
    -- números se repetem entre agrupamentos, não dentro de um (também serve de índice por agrupamento_id)
    CONSTRAINT uk_subdivisao_agrupamento_numero UNIQUE (agrupamento_id, numero),
    CONSTRAINT uk_subdivisao_agrupamento_nome UNIQUE (agrupamento_id, nome)
);

ALTER TABLE congregacao ADD COLUMN subdivisao_id UUID REFERENCES subdivisao (id);
CREATE INDEX idx_congregacao_subdivisao ON congregacao (subdivisao_id);

-- As 74 áreas da IEAD-PE (mais qualquer número já existente na tabela `area`).
-- O nome antigo ("Área 40") some: o tipo AREA só tem número.
INSERT INTO agrupamento (tipo, numero, ativo)
SELECT 'AREA', n.numero, COALESCE(a.ativa, TRUE)
FROM (SELECT generate_series(1, 74) AS numero
      UNION
      SELECT numero FROM area) n
LEFT JOIN area a ON a.numero = n.numero;

-- Subdivisão implícita (sem número/nome) de cada agrupamento AREA.
INSERT INTO subdivisao (agrupamento_id)
SELECT id FROM agrupamento WHERE tipo = 'AREA';

-- Congregações existentes passam a apontar para a subdivisão da sua área.
UPDATE congregacao c
SET subdivisao_id = s.id
FROM area a
JOIN agrupamento g ON g.tipo = 'AREA' AND g.numero = a.numero
JOIN subdivisao s ON s.agrupamento_id = g.id
WHERE c.area_id = a.id;
