# Gestion scolaire — lycée

Application web de gestion scolaire, académique et financière pour un établissement d'enseignement secondaire.

| Dossier | Contenu |
|---|---|
| `backend/` | API REST Java 21 / Spring Boot 3 (Maven), PostgreSQL, Flyway, Hibernate Envers |
| `frontend/` | Interface Angular 20 (connexion Keycloak) |
| `docker/`, `docker-compose.yml` | PostgreSQL 16 et Keycloak 26 pour le développement |
| `.github/workflows/ci.yml` | Compilation et tests automatiques à chaque envoi sur GitHub |

## État d'avancement

- **Lot 1 — Socle (ce dépôt)** : schéma complet des 22 tables et de leurs tables d'audit, sécurité par rôles Keycloak, audit à deux niveaux, gestion des années scolaires, squelette Angular avec connexion.
- Lot 2 — Paramétrage : trimestres, niveaux, classes, personnel, élèves, imports Excel.
- Lot 3 — Finances : types de frais, grille tarifaire nouveau/ancien, plans de tranches, encaissement, reçus, impayés.
- Lot 4 — Notes : évaluations, saisie, moyennes, classement, bulletins PDF.

## Démarrer en développement

Prérequis : Docker, Java 21, Maven 3.9+, Node.js 22.

```bash
# 1. Base de données et Keycloak
docker compose up -d

# 2. API (http://localhost:8080)
cd backend
mvn spring-boot:run

# 3. Interface (http://localhost:4200)
cd frontend
npm install
npm start
```

Keycloak est accessible sur http://localhost:8081 (console d'administration : `admin` / `admin`).
Le realm `lycee` est importé au premier démarrage avec les six rôles et quatre comptes de démonstration :

| Compte | Rôle |
|---|---|
| `directeur` | DIRECTEUR |
| `intendant` | INTENDANT |
| `econome` | ECONOME |
| `enseignant` | ENSEIGNANT |

Mot de passe initial de ces comptes : `Changeme123!` (à changer à la première connexion).

> **Avant toute mise en production** : changer les mots de passe de `docker-compose.yml`, le secret du client
> `lycee-backend`, supprimer les comptes de démonstration et activer HTTPS.

## Tests

```bash
cd backend
mvn test        # nécessite PostgreSQL démarré (base lycee_test créée par docker compose)
```

Au démarrage, l'API vérifie que les entités Java correspondent exactement au schéma de la base
(`ddl-auto: validate`) : toute divergence fait échouer les tests.

## Configuration

| Variable | Valeur par défaut | Rôle |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/lycee` | Base de données |
| `DB_USER` / `DB_PASSWORD` | `lycee` / `lycee` | Identifiants de la base |
| `KEYCLOAK_ISSUER` | `http://localhost:8081/realms/lycee` | Émetteur des jetons JWT |
| `CORS_ORIGINES` | `http://localhost:4200` | Origines autorisées pour l'interface |

Côté interface, les adresses de l'API et de Keycloak sont dans `frontend/src/app/core/config.ts`.

## Repères dans le code

- `backend/src/main/resources/db/migration/` : schéma (V1), tables d'audit (V2), données de référence (V3).
- `backend/.../domain/` : les 22 entités ; `AbstractAuditingEntity` porte les colonnes d'audit.
- `backend/.../domain/audit/` : table `revinfo` enrichie de l'utilisateur Keycloak et de l'adresse IP.
- `backend/.../security/Roles.java` : expressions de droits utilisées dans `@PreAuthorize`.
- `frontend/src/app/core/` : connexion Keycloak, intercepteur HTTP, garde par rôle.

## API disponible (lot 1)

| Méthode | Chemin | Accès |
|---|---|---|
| GET | `/api/moi` | Tout utilisateur connecté |
| GET | `/api/annees-scolaires` | Tout utilisateur connecté |
| GET | `/api/annees-scolaires/active` | Tout utilisateur connecté |
| POST | `/api/annees-scolaires` | Directeur, Censeur |
| POST | `/api/annees-scolaires/{id}/activation` | Directeur, Censeur |
| GET | `/api/annees-scolaires/{id}/historique` | Directeur, Censeur |
