# Gestion scolaire — lycée

Application web de gestion scolaire, académique et financière pour un établissement d'enseignement secondaire.

| Dossier | Contenu |
|---|---|
| `backend/` | API REST Java 17 / Spring Boot 3 (Maven), PostgreSQL, Flyway, Hibernate Envers |
| `frontend/` | Interface Angular 20 bâtie sur le gabarit [ng-matero](https://ng-matero.github.io/ng-matero/) (Angular Material, licence MIT), connexion Keycloak |
| `docker/`, `docker-compose.yml` | PostgreSQL 16 et Keycloak 26 pour le développement |
| `.github/workflows/ci.yml` | Compilation et tests automatiques à chaque envoi sur GitHub |

## État d'avancement

- **Lot 1 — Socle (fait)** : schéma complet des 22 tables et de leurs tables d'audit, sécurité par rôles Keycloak, audit à deux niveaux, gestion des années scolaires, squelette Angular avec connexion.
- **Lot 2 — Paramétrage (fait)** : trimestres et option Composition (par année, trimestre ou cycle), niveaux, classes, personnel, élèves, inscriptions, imports Excel/CSV en deux étapes.
- Lot 3 — Finances : types de frais, grille tarifaire nouveau/ancien, plans de tranches, encaissement, reçus, impayés.
- Lot 4 — Notes : évaluations, saisie, moyennes, classement, bulletins PDF.

## Démarrer en développement

Prérequis : Docker, Java 17, Maven 3.9+, Node.js 22.

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
En production, elles peuvent être changées sans recompiler, en définissant `window.__LYCEE_CONFIG__`
dans `index.html` (un exemple y est déjà écrit en commentaire).

## Repères dans le code

- `backend/src/main/resources/db/migration/` : schéma (V1), tables d'audit (V2), données de référence (V3).
- `backend/.../domain/` : les 22 entités ; `AbstractAuditingEntity` porte les colonnes d'audit.
- `backend/.../domain/audit/` : table `revinfo` enrichie de l'utilisateur Keycloak et de l'adresse IP.
- `backend/.../security/Roles.java` : expressions de droits utilisées dans `@PreAuthorize`.
- `frontend/src/app/core/` : connexion Keycloak, intercepteur HTTP, gardes de route, menu latéral (`bootstrap/menu-lycee.ts`).
- `frontend/src/app/routes/` : les écrans, un dossier par domaine.
- `frontend/src/app/theme/` : coquille du gabarit (bandeau, menu, panneau de réglages).
- `frontend/public/i18n/fr-FR.json` : libellés du menu et du gabarit.

## Interface

L'interface reprend le gabarit d'administration **ng-matero** (Angular Material, licence MIT) :
menu latéral repliable, bandeau fixe, thème clair ou sombre, fil d'Ariane et panneau de réglages
(bouton en bas à droite). Trois adaptations ont été faites :

- la connexion par mot de passe du gabarit est remplacée par Keycloak ;
- le menu est construit à partir des rôles de la personne connectée (`menu-lycee.ts`) ;
- l'année scolaire de travail se choisit dans le bandeau et s'applique à toutes les pages.

La police Roboto et les icônes Material sont servies par l'application : aucun appel à Internet
au démarrage.

## Imports Excel / CSV

Les imports acceptent les fichiers `.xlsx`, `.xls` et `.csv` (séparateur `;` ou `,`, encodage UTF-8).
La première ligne contient les en-têtes ; la casse, les accents et l'ordre des colonnes sont libres.

| Import | Colonnes obligatoires | Colonnes facultatives |
|---|---|---|
| Personnel | Matricule, Nom, Prénom, Email, Rôle | Téléphone |
| Élèves d'une classe | Matricule, Nom, Prénom, Date de naissance (JJ/MM/AAAA), Sexe (M/F) | Parent, Contact, Redoublant (oui/non) |

L'import se fait en deux étapes : « Analyser » affiche l'aperçu et les erreurs sans rien enregistrer,
puis « Importer » enregistre. Si une seule ligne est en erreur, aucune ligne n'est importée.

À l'import des élèves, la catégorie **nouveau / ancien** est calculée automatiquement : un élève est
« ancien » s'il était inscrit dans l'établissement l'année scolaire précédente. L'intendant peut la
corriger, avec un motif.

## Comptes Keycloak du personnel

Par défaut, l'application n'écrit rien dans Keycloak : les comptes de connexion sont créés à la main.
Pour qu'ils soient créés automatiquement à l'ajout ou à l'import du personnel (mot de passe temporaire
à changer à la première connexion, rôle attribué), définir :

| Variable | Rôle |
|---|---|
| `KEYCLOAK_PROVISIONNEMENT=true` | Active la création automatique |
| `KEYCLOAK_URL` | Adresse de Keycloak (défaut `http://localhost:8081`) |
| `KEYCLOAK_CLIENT_ID` / `KEYCLOAK_CLIENT_SECRET` | Client confidentiel `lycee-backend` |
| `KEYCLOAK_MOT_DE_PASSE_INITIAL` | Mot de passe temporaire des nouveaux comptes |

L'identifiant de connexion est l'adresse email de la personne.

## API disponible

| Méthode | Chemin | Accès |
|---|---|---|
| GET | `/api/moi` | Tout utilisateur connecté |
| GET | `/api/annees-scolaires`, `/api/annees-scolaires/active` | Tout utilisateur connecté |
| POST | `/api/annees-scolaires`, `/api/annees-scolaires/{id}/activation` | Direction |
| GET | `/api/annees-scolaires/{id}/historique` | Direction |
| GET | `/api/annees-scolaires/{id}/trimestres` | Tout utilisateur connecté |
| POST | `/api/annees-scolaires/{id}/trimestres` | Direction |
| PUT | `/api/trimestres/{id}/composition` | Direction |
| PUT, DELETE | `/api/trimestres/{id}/composition/{cycle}` | Direction |
| POST | `/api/trimestres/{id}/cloture`, `/api/trimestres/{id}/reouverture` | Direction |
| GET | `/api/niveaux`, `/api/annees-scolaires/{id}/classes` | Tout utilisateur connecté |
| POST, PUT | `/api/niveaux`, `/api/annees-scolaires/{id}/classes`, `/api/classes/{id}` | Direction |
| GET, POST, PUT | `/api/personnel`, `/api/personnel/{id}`, activation / désactivation | Direction |
| POST | `/api/personnel/import?simulation=true|false` | Direction |
| GET | `/api/eleves?recherche=`, `/api/classes/{id}/inscriptions` | Tout utilisateur connecté |
| POST | `/api/classes/{id}/inscriptions` | Direction, Surveillant |
| POST | `/api/classes/{id}/inscriptions/import?simulation=true|false` | Direction, Surveillant |
| PUT | `/api/inscriptions/{id}/categorie` | Intendant |

« Direction » = Directeur ou Censeur.
