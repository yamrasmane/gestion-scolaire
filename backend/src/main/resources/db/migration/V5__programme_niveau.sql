-- =====================================================================
-- Programme d'un niveau : les matières enseignées à un « type de classe »
-- (6ème, 1ère D, …) et leur coefficient (SF-09).
--
-- Une classe reçoit ses matières en appliquant le programme de son niveau ;
-- le coefficient reste modifiable classe par classe ensuite.
-- =====================================================================

CREATE TABLE programme_niveau (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    niveau_id   UUID         NOT NULL REFERENCES niveau (id),
    matiere_id  UUID         NOT NULL REFERENCES matiere (id),
    coefficient NUMERIC(4,1) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE, updated_by VARCHAR(100), version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_programme_niveau UNIQUE (niveau_id, matiere_id),
    CONSTRAINT ck_programme_coefficient CHECK (coefficient > 0)
);

COMMENT ON TABLE programme_niveau IS
    'Matières et coefficients applicables à toutes les classes d''un niveau.';

-- Table miroir pour la piste d'audit, dans le schéma dédié.
CREATE TABLE audit.programme_niveau_aud (
    LIKE public.programme_niveau EXCLUDING CONSTRAINTS EXCLUDING INDEXES
);
ALTER TABLE audit.programme_niveau_aud
    ADD COLUMN rev_id  INTEGER NOT NULL REFERENCES audit.revinfo (rev_id),
    ADD COLUMN revtype SMALLINT;
ALTER TABLE audit.programme_niveau_aud ALTER COLUMN id SET NOT NULL;
ALTER TABLE audit.programme_niveau_aud ADD PRIMARY KEY (id, rev_id);

DO $$
DECLARE
    colonne TEXT;
BEGIN
    -- Dans une table d'audit, toute colonne peut être absente d'une révision.
    FOREACH colonne IN ARRAY ARRAY['niveau_id', 'matiere_id', 'coefficient',
                                   'created_at', 'created_by', 'version']
    LOOP
        EXECUTE format('ALTER TABLE audit.programme_niveau_aud ALTER COLUMN %I DROP NOT NULL',
                       colonne);
    END LOOP;
END $$;
