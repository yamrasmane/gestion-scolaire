-- =====================================================================
-- Piste d'audit (SF-20) : table centrale de révision + tables miroirs
-- <table>_aud alimentées par Hibernate Envers.
-- =====================================================================

CREATE SEQUENCE revinfo_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE revinfo (
    rev_id            INTEGER NOT NULL PRIMARY KEY,
    rev_tstmp         BIGINT  NOT NULL,
    keycloak_user_id  VARCHAR(100),
    username          VARCHAR(100),
    ip_address        VARCHAR(45)
);

-- Chaque table miroir reprend les colonnes de la table métier (sans la
-- colonne technique "version", non historisée par Envers), rendues
-- facultatives, plus le numéro de révision et le type d'action
-- (revtype : 0 = création, 1 = modification, 2 = suppression).
DO $$
DECLARE
    t   TEXT;
    col TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'annee_scolaire', 'trimestre', 'config_composition_cycle', 'niveau', 'classe',
        'personnel', 'eleve', 'inscription', 'matiere', 'enseignement', 'evaluation',
        'note', 'bulletin', 'lot_impression',
        'type_frais', 'grille_tarifaire', 'plan_paiement', 'tranche_paiement',
        'frais_eleve', 'echeance_eleve', 'paiement', 'imputation_paiement']
    LOOP
        EXECUTE format('CREATE TABLE %I (LIKE %I)', t || '_aud', t);
        EXECUTE format('ALTER TABLE %I DROP COLUMN version', t || '_aud');
        FOR col IN
            SELECT column_name FROM information_schema.columns
            WHERE table_schema = current_schema() AND table_name = t || '_aud'
              AND is_nullable = 'NO' AND column_name <> 'id'
        LOOP
            EXECUTE format('ALTER TABLE %I ALTER COLUMN %I DROP NOT NULL', t || '_aud', col);
        END LOOP;
        EXECUTE format('ALTER TABLE %I ADD COLUMN rev_id INTEGER NOT NULL REFERENCES revinfo (rev_id)', t || '_aud');
        EXECUTE format('ALTER TABLE %I ADD COLUMN revtype SMALLINT', t || '_aud');
        EXECUTE format('ALTER TABLE %I ADD PRIMARY KEY (id, rev_id)', t || '_aud');
    END LOOP;
END $$;
