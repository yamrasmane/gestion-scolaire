-- Exécuté une seule fois, à la première création du volume PostgreSQL.
-- La base « lycee » est créée par les variables d'environnement du conteneur.
CREATE DATABASE lycee_test OWNER lycee;
CREATE DATABASE keycloak OWNER lycee;
