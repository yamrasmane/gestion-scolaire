-- Appréciations portées au bulletin, réglées par intervalles de moyenne, et
-- versionnement par année des paramètres qui changent d'une année à l'autre.

-- 1. Barème d'appréciations (SF-17) : une grille par année scolaire, l'une
--    pour les moyennes de matière, l'autre pour la moyenne générale.
CREATE TABLE bareme_appreciation (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    annee_scolaire_id UUID         NOT NULL REFERENCES annee_scolaire (id),
    -- MATIERE : appréciation d'une moyenne de matière ; GENERALE : mention
    -- portée sur la moyenne générale.
    portee            VARCHAR(20)  NOT NULL,
    -- Intervalle [borne_min, borne_max[ ; la borne haute 20 est incluse.
    borne_min         NUMERIC(5,2) NOT NULL,
    borne_max         NUMERIC(5,2) NOT NULL,
    libelle           VARCHAR(60)  NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE, updated_by VARCHAR(100), version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_bareme_portee CHECK (portee IN ('MATIERE', 'GENERALE')),
    CONSTRAINT ck_bareme_bornes CHECK (borne_min >= 0 AND borne_max <= 20 AND borne_max > borne_min),
    CONSTRAINT ux_bareme_appreciation UNIQUE (annee_scolaire_id, portee, borne_min)
);

COMMENT ON TABLE bareme_appreciation IS
    'Appréciations du bulletin par intervalle de moyenne, propres à une année scolaire.';

CREATE INDEX ix_bareme_annee_portee ON bareme_appreciation (annee_scolaire_id, portee, borne_min);

CREATE TABLE audit.bareme_appreciation_aud
    (LIKE public.bareme_appreciation EXCLUDING CONSTRAINTS EXCLUDING INDEXES);
ALTER TABLE audit.bareme_appreciation_aud
    ADD COLUMN rev_id INTEGER NOT NULL REFERENCES audit.revinfo (rev_id),
    ADD COLUMN revtype SMALLINT;
ALTER TABLE audit.bareme_appreciation_aud ADD PRIMARY KEY (id, rev_id);
DO $$
DECLARE colonne TEXT;
BEGIN
    FOREACH colonne IN ARRAY ARRAY['annee_scolaire_id', 'portee', 'borne_min', 'borne_max',
                                   'libelle', 'created_at', 'created_by', 'version']
        LOOP
            EXECUTE format('ALTER TABLE audit.bareme_appreciation_aud ALTER COLUMN %I DROP NOT NULL',
                           colonne);
        END LOOP;
END $$;

-- 2. Le programme d'un niveau change d'une année à l'autre : il est désormais
--    rattaché à une année scolaire, comme les classes ou la grille tarifaire.
ALTER TABLE programme_niveau
    ADD COLUMN annee_scolaire_id UUID REFERENCES annee_scolaire (id);
ALTER TABLE audit.programme_niveau_aud ADD COLUMN annee_scolaire_id UUID;

-- Les programmes déjà saisis sont rattachés à l'année active, sinon à la plus
-- récente ; sans aucune année, la table est forcément vide.
UPDATE programme_niveau
SET annee_scolaire_id = COALESCE(
        (SELECT id FROM annee_scolaire WHERE actif LIMIT 1),
        (SELECT id FROM annee_scolaire ORDER BY date_debut DESC LIMIT 1))
WHERE annee_scolaire_id IS NULL;

DELETE FROM programme_niveau WHERE annee_scolaire_id IS NULL;

ALTER TABLE programme_niveau ALTER COLUMN annee_scolaire_id SET NOT NULL;
ALTER TABLE programme_niveau DROP CONSTRAINT ux_programme_niveau;
ALTER TABLE programme_niveau
    ADD CONSTRAINT ux_programme_niveau UNIQUE (annee_scolaire_id, niveau_id, matiere_id);

COMMENT ON TABLE programme_niveau IS
    'Matières et coefficients d''un niveau pour une année scolaire donnée.';
