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
- **Lot 3 — Finances (fait)** : catalogue des frais, grille tarifaire par niveau ou classe et par
  catégorie nouveau/ancien (avec reconduction d'une année sur l'autre), plans de paiement par
  tranches, échéancier par élève, remises, encaissement et imputation, reçus, annulations,
  impayés et journal de caisse.
- **Lot 4 — Notes (fait)** : matières et coefficients par classe, évaluations (devoirs et
  composition), grille de saisie des notes, moyennes avec ou sans composition, moyenne générale
  pondérée, classement avec ex æquo, bulletins imprimables et délibération.

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

## Matières, programmes et affectations

Le **programme d'un niveau** (« Paramétrage › Programmes par niveau ») décrit les matières d'un
type de classe et leur coefficient : une ligne par matière, un coefficient par niveau. Toute classe
créée ensuite dans ce niveau reçoit ces matières automatiquement ; le bouton *Appliquer aux classes
existantes* complète celles qui existent déjà, sans toucher aux professeurs déjà nommés (cocher la
case pour réaligner aussi les coefficients).

Les professeurs sont nommés classe par classe dans « Notes › Affectation des enseignants » : un
choix par matière, enregistré en une seule fois pour toute la classe. La même page donne le service
d'un professeur — ses classes, ses matières et son total de coefficients.

Les **fiches du personnel** (« Personnel ») se modifient en place : nom, prénom, adresse, téléphone
et rôle. Changer le rôle met aussi à jour les droits du compte Keycloak lié, s'il existe.

## Listes, recherche et saisie

Les listes longues (élèves d'une classe, personnel, impayés, journal de caisse) sont **paginées par
l'API** : le navigateur ne reçoit qu'une page à la fois (25 lignes par défaut, 10 à 100 au choix).
Les réponses ont toutes la même forme — `contenu`, `page`, `taille`, `total`, `pages` — et acceptent
les paramètres `page`, `size` et `sort` (par exemple `sort=nom,desc`).

Chaque tableau porte une **barre de recherche multicritère** : une recherche libre (matricule, nom,
adresse, téléphone selon la liste) et des filtres qui se cumulent — rôle et état du compte pour le
personnel, statut et redoublants pour les inscriptions, sexe et intervalle de naissance pour les
élèves, classe et date limite pour les impayés. Le tri des colonnes et la recherche sont faits en
base, jamais sur la page affichée. L'export CSV des impayés redemande à l'API toutes les lignes de
la recherche, pas seulement la page à l'écran.

Les listes déroulantes qui peuvent être longues (classes, élèves, matières, professeurs) **se
cherchent au clavier**, et toutes les dates se saisissent dans un **calendrier** en français.
L'interface s'adapte au téléphone : sur petit écran, chaque ligne de tableau devient une fiche dont
les valeurs portent le nom de leur colonne.

## Règles de calcul

**Moyenne d'une matière** (SF-13) : seules les notes saisies et non marquées « absent » comptent.
Sans composition, c'est la moyenne des devoirs. Avec composition et un poids *p* :
`(moyenne des devoirs + p × composition) / (1 + p)`. Sans aucune note, la matière est « non
classée » et sort du calcul.

**Moyenne générale** (SF-14) : `Σ(moyenne × coefficient) / Σ(coefficient)`, arrondie à deux
décimales, les matières non classées exclues.

**Classement** (SF-15, SF-16) : rang par matière et au général, par compétition standard — deux
élèves à égalité partagent le rang, le suivant prend le rang suivant du nombre d'ex æquo.

**Échéancier** (SF-27) : les frais non fractionnables sont dus en totalité à la première tranche ;
les autres sont répartis selon le plan, l'écart d'arrondi étant porté sur la dernière tranche.

**Imputation d'un règlement** (SF-29) : l'échéance la plus ancienne d'abord, puis, à date égale,
selon l'ordre de priorité des types de frais. Le caissier peut aussi imputer sur un frais précis.

**Reçus et bulletins** : ils s'impriment depuis le navigateur (impression ou enregistrement en
PDF). La génération par lot en PDF ou ZIP n'est pas encore faite.

## Rôles

| Rôle | Ce qu'il peut faire |
|---|---|
| `ADMIN` | Tout. Chaque contrôle d'autorisation l'accepte, côté API comme côté interface. |
| `DIRECTEUR` | Années, trimestres, classes, matières, personnel, délibérations ; valide les remises importantes. |
| `CENSEUR` | Comme le Directeur, sauf la validation des remises au-delà du seuil. |
| `INTENDANT` | Types de frais, grille tarifaire, plans de paiement, remises, annulations de règlement. |
| `ECONOME` | Encaissement et consultation des situations financières. |
| `ENSEIGNANT` | Évaluations et notes des matières dont il est le professeur titulaire. |
| `SURVEILLANT` | Élèves et inscriptions. |

Les rôles viennent du jeton Keycloak. Un compte sans rôle peut se connecter mais ne voit qu'un
menu réduit, et l'application le lui signale. La page d'accueil affiche toujours le compte connecté
et ses rôles : c'est le premier endroit à regarder quand une entrée de menu manque.

Un compte porteur du rôle `ADMIN` reçoit à l'ouverture de session **tous les rôles** comme
permissions d'affichage : il voit donc l'intégralité du menu, y compris les entrées ajoutées plus
tard, sans qu'il faille penser à y inscrire `ADMIN`.

### Donner le rôle ADMIN à un compte

Le realm n'est importé qu'à la **première** création de la base Keycloak : si votre realm `lycee`
existe déjà, le compte `admin` livré avec le fichier d'import n'y figure pas. Deux solutions.

Dans la console Keycloak (http://localhost:8081, `admin` / `admin`), realm `lycee` : *Users* →
le compte → onglet *Role mapping* → *Assign role* → filtrer sur les rôles du realm → `ADMIN`. La
personne doit se déconnecter puis se reconnecter pour que son nouveau jeton porte le rôle.

Ou réimporter le realm en repartant d'une base Keycloak vierge — attention, cela supprime tous les
comptes et mots de passe déjà créés dans Keycloak (les données du lycée, elles, ne bougent pas) :

```bash
docker compose stop keycloak
psql -U lycee -h localhost -d postgres -c "DROP DATABASE keycloak;" -c "CREATE DATABASE keycloak;"
docker compose up -d keycloak
```

## Identifiants

Toutes les clés primaires et étrangères sont des **UUID** (`uuid` PostgreSQL), y compris dans
les tables d'audit. Seul le numéro de révision (`rev_id`) reste un entier, parce qu'Hibernate
Envers numérote les révisions de façon croissante.

Les identifiants sont engendrés par l'application à l'insertion, en UUID ordonné dans le temps
(`@UuidGenerator(style = TIME)`) : deux enregistrements créés l'un après l'autre ont des
identifiants voisins, ce qui évite la fragmentation des index que provoquerait un UUID purement
aléatoire. La base pose malgré tout un `DEFAULT gen_random_uuid()`, utile pour les insertions
faites directement en SQL.

Ce que cela change à l'usage : un identifiant ne se devine plus et ne révèle plus le volume de
la base (`/eleves/312` renseignait sur le nombre d'élèves), et deux établissements peuvent
fusionner leurs bases sans collision d'identifiants.

## Piste d'audit

L'historique des modifications (Hibernate Envers) vit dans un schéma PostgreSQL à part,
`audit` : une table `audit.<table>_aud` par table métier, plus `audit.revinfo` qui porte
l'auteur (identifiant et nom Keycloak), l'adresse IP et l'horodatage de chaque transaction.

Le schéma `public` ne contient donc que les 22 tables métier, ce qui garde la base lisible et
permet de traiter l'historique à part : sauvegarde séparée, et accès en lecture seule donné à
un auditeur sans ouvrir les tables métier. Par exemple :

```sql
CREATE ROLE auditeur LOGIN PASSWORD '...';
GRANT USAGE ON SCHEMA audit TO auditeur;
GRANT SELECT ON ALL TABLES IN SCHEMA audit TO auditeur;
```

Pour lire l'historique d'un enregistrement :

```sql
SELECT a.rev_id, r.username, to_timestamp(r.rev_tstmp / 1000) AS quand, a.*
FROM audit.inscription_aud a JOIN audit.revinfo r ON r.rev_id = a.rev_id
WHERE a.id = '3f2b…' ORDER BY a.rev_id;
```

Le déplacement est fait par la migration `V4__schema_audit.sql` : une base déjà en service le
reprend toute seule au démarrage suivant de l'API.

## Jeu de données de démonstration

`backend/src/main/resources/db/donnees-demo.sql` remplit une base vierge avec un établissement
complet et inventé : deux années scolaires, 8 classes et 144 élèves cette année, le personnel,
les matières et coefficients, les notes du 1er trimestre, la grille tarifaire, les plans de
paiement, les frais par élève, les échéanciers et les règlements déjà encaissés (soldés,
partiels, en retard, un règlement annulé).

Démarrez d'abord l'API une fois pour que Flyway crée les tables, arrêtez-la, puis :

```bash
psql -U lycee -h localhost -d lycee -f backend/src/main/resources/db/donnees-demo.sql
```

Le script refuse de s'exécuter si la base contient déjà des données. Pour repartir de zéro :

```bash
psql -U lycee -h localhost -d lycee -c "TRUNCATE TABLE annee_scolaire, personnel, eleve, matiere, revinfo RESTART IDENTITY CASCADE;"
```

Un compte **`admin`** existe aussi, porteur du rôle `ADMIN` : il ouvre toutes les fonctions de
l'application, y compris celles réservées à la direction, à l'intendance, à la caisse ou au
professeur titulaire d'une matière. Les comptes de démonstration de Keycloak (`directeur`,
`intendant`, `econome`, `enseignant`)
correspondent à des fiches du personnel : le rattachement se fait tout seul à la première
connexion, par l'adresse électronique. Le compte `enseignant` est professeur de mathématiques
en 6ème A et 6ème B, et peut donc y saisir des notes.

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
