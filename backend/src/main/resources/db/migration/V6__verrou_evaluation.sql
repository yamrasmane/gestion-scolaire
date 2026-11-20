-- Verrouillage d'une épreuve : une fois verrouillée, ses notes ne peuvent plus
-- être modifiées ni complétées, sauf déverrouillage par la direction (SF-12).
ALTER TABLE evaluation
    ADD COLUMN verrouillee BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE audit.evaluation_aud
    ADD COLUMN verrouillee BOOLEAN;
