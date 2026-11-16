-- Données de référence : niveaux usuels et types de frais courants.
-- Tout reste modifiable depuis l'application.

INSERT INTO niveau (code, libelle, cycle, ordre, created_at, created_by) VALUES
    ('6EME',   'Sixième',     'PREMIER', 1, now(), 'system'),
    ('5EME',   'Cinquième',   'PREMIER', 2, now(), 'system'),
    ('4EME',   'Quatrième',   'PREMIER', 3, now(), 'system'),
    ('3EME',   'Troisième',   'PREMIER', 4, now(), 'system'),
    ('2NDE_A', 'Seconde A',   'SECOND',  5, now(), 'system'),
    ('2NDE_C', 'Seconde C',   'SECOND',  6, now(), 'system'),
    ('1ERE_A', 'Première A',  'SECOND',  7, now(), 'system'),
    ('1ERE_D', 'Première D',  'SECOND',  8, now(), 'system'),
    ('TLE_A',  'Terminale A', 'SECOND',  9, now(), 'system'),
    ('TLE_D',  'Terminale D', 'SECOND', 10, now(), 'system');

INSERT INTO type_frais (code, libelle, obligatoire, fractionnable, priorite_imputation, created_at, created_by) VALUES
    ('INSCRIPTION', 'Frais d''inscription',  TRUE,  FALSE, 10, now(), 'system'),
    ('APE',         'Cotisation APE',        TRUE,  FALSE, 20, now(), 'system'),
    ('SCOLARITE',   'Frais de scolarité',    TRUE,  TRUE,  30, now(), 'system'),
    ('BIBLIO',      'Frais de bibliothèque', TRUE,  FALSE, 40, now(), 'system'),
    ('TENUE',       'Tenue scolaire',        FALSE, FALSE, 50, now(), 'system');
