-- =====================================================================
-- La piste d'audit passe dans son propre schéma « audit » (SF-20).
--
-- Les tables métier restent dans « public » ; les tables miroirs <table>_aud
-- et la table de révision rejoignent « audit ». Les clés étrangères suivent
-- les tables : rien d'autre n'est à reprendre.
--
-- Intérêt : sauvegardes et droits séparés (l'historique peut être donné en
-- lecture seule à un auditeur), et une liste de tables métier lisible.
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS audit;

COMMENT ON SCHEMA audit IS
    'Historique des modifications (Hibernate Envers) : une ligne par version de chaque enregistrement.';

DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'annee_scolaire', 'trimestre', 'config_composition_cycle', 'niveau', 'classe',
        'personnel', 'eleve', 'inscription', 'matiere', 'enseignement', 'evaluation',
        'note', 'bulletin', 'lot_impression',
        'type_frais', 'grille_tarifaire', 'plan_paiement', 'tranche_paiement',
        'frais_eleve', 'echeance_eleve', 'paiement', 'imputation_paiement']
    LOOP
        EXECUTE format('ALTER TABLE public.%I SET SCHEMA audit', t || '_aud');
    END LOOP;
END $$;

ALTER TABLE public.revinfo SET SCHEMA audit;
ALTER SEQUENCE public.revinfo_seq SET SCHEMA audit;
