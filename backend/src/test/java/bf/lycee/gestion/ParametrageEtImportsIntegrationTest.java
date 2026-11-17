package bf.lycee.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Lot 2 : trimestres et composition, niveaux, classes, personnel, élèves et imports. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ParametrageEtImportsIntegrationTest {

    private static final RequestPostProcessor DIRECTEUR = utilisateur("le.directeur", "DIRECTEUR");
    private static final RequestPostProcessor ENSEIGNANT = utilisateur("prof.math", "ENSEIGNANT");
    private static final RequestPostProcessor SURVEILLANT = utilisateur("le.surveillant", "SURVEILLANT");
    private static final RequestPostProcessor INTENDANT = utilisateur("l.intendant", "INTENDANT");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID annee2025;
    private UUID annee2026;

    @BeforeEach
    void preparer() throws Exception {
        jdbc.execute("TRUNCATE TABLE annee_scolaire, personnel, eleve, audit.revinfo RESTART IDENTITY CASCADE");
        annee2025 = creerAnnee("2025-2026", "2025-10-01", "2026-07-15");
        annee2026 = creerAnnee("2026-2027", "2026-10-01", "2027-07-15");
    }

    private static RequestPostProcessor utilisateur(String nom, String role) {
        return jwt()
                .jwt(jeton -> jeton.subject("id-" + nom).claim("preferred_username", nom))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private UUID creerAnnee(String libelle, String debut, String fin) throws Exception {
        mvc.perform(post("/api/annees-scolaires").with(DIRECTEUR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelle\": \"%s\", \"dateDebut\": \"%s\", \"dateFin\": \"%s\"}"
                                .formatted(libelle, debut, fin)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM annee_scolaire WHERE libelle = ?", UUID.class, libelle);
    }

    private UUID creerClasse(UUID anneeId, String niveau, String nom, int effectifMax) throws Exception {
        UUID niveauId = jdbc.queryForObject("SELECT id FROM niveau WHERE code = ?", UUID.class, niveau);
        mvc.perform(post("/api/annees-scolaires/" + anneeId + "/classes").with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"niveauId\": %d, \"nom\": \"%s\", \"effectifMax\": %d}"
                                .formatted(niveauId, nom, effectifMax)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM classe WHERE annee_scolaire_id = ? AND nom = ?",
                UUID.class, anneeId, nom);
    }

    private static MockMultipartFile csv(String nom, String contenu) {
        return new MockMultipartFile("fichier", nom, "text/csv", contenu.getBytes(StandardCharsets.UTF_8));
    }

    /** Classeur Excel dont la colonne « Date de naissance » contient de vraies dates. */
    private static MockMultipartFile excelEleves(Object[]... eleves) throws Exception {
        try (XSSFWorkbook classeur = new XSSFWorkbook(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            Sheet feuille = classeur.createSheet("Élèves");
            CellStyle styleDate = classeur.createCellStyle();
            styleDate.setDataFormat(classeur.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
            String[] entetes = {"Matricule", "Nom", "Prénoms", "Date de naissance", "Sexe", "Parent", "Contact",
                    "Redoublant"};
            Row ligneEntetes = feuille.createRow(0);
            for (int c = 0; c < entetes.length; c++) {
                ligneEntetes.createCell(c).setCellValue(entetes[c]);
            }
            for (int r = 0; r < eleves.length; r++) {
                Row ligne = feuille.createRow(r + 1);
                for (int c = 0; c < eleves[r].length; c++) {
                    Cell cellule = ligne.createCell(c);
                    if (eleves[r][c] instanceof LocalDate date) {
                        cellule.setCellValue(date);
                        cellule.setCellStyle(styleDate);
                    } else {
                        cellule.setCellValue(String.valueOf(eleves[r][c]));
                    }
                }
            }
            classeur.write(sortie);
            return new MockMultipartFile("fichier", "eleves.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", sortie.toByteArray());
        }
    }

    // ---------- Trimestres et option Composition (SF-02, SF-03) ----------

    @Test
    void lesTrimestresNeSeChevauchentPasEtSontReservesALaDirection() throws Exception {
        String t1 = "{\"numero\": 1, \"dateDebut\": \"2026-10-01\", \"dateFin\": \"2026-12-20\"}";
        mvc.perform(post("/api/annees-scolaires/" + annee2026 + "/trimestres").with(ENSEIGNANT)
                .contentType(MediaType.APPLICATION_JSON).content(t1)).andExpect(status().isForbidden());
        mvc.perform(post("/api/annees-scolaires/" + annee2026 + "/trimestres").with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON).content(t1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cloture").value(false));
        // même numéro
        mvc.perform(post("/api/annees-scolaires/" + annee2026 + "/trimestres").with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\": 1, \"dateDebut\": \"2027-01-05\", \"dateFin\": \"2027-03-20\"}"))
                .andExpect(status().isConflict());
        // dates qui chevauchent le trimestre 1
        mvc.perform(post("/api/annees-scolaires/" + annee2026 + "/trimestres").with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\": 2, \"dateDebut\": \"2026-12-15\", \"dateFin\": \"2027-03-20\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("chevauchent")));
        // hors de l'année scolaire
        mvc.perform(post("/api/annees-scolaires/" + annee2026 + "/trimestres").with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\": 3, \"dateDebut\": \"2027-04-01\", \"dateFin\": \"2027-08-30\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/annees-scolaires/" + annee2026 + "/trimestres").with(ENSEIGNANT))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void laCompositionSuitLaPrioriteCyclePuisTrimestrePuisAnnee() throws Exception {
        mvc.perform(post("/api/annees-scolaires/" + annee2026 + "/trimestres").with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\": 1, \"dateDebut\": \"2026-10-01\", \"dateFin\": \"2026-12-20\"}"))
                .andExpect(status().isCreated())
                // par défaut : réglage de l'année (composition activée, poids 2)
                .andExpect(jsonPath("$.compositionParCycle.PREMIER.source").value("ANNEE"))
                .andExpect(jsonPath("$.compositionParCycle.PREMIER.avecComposition").value(true))
                .andExpect(jsonPath("$.compositionParCycle.SECOND.poidsComposition").value(2.0));
        UUID t1 = jdbc.queryForObject("SELECT id FROM trimestre", UUID.class);

        // réglage du trimestre : composition désactivée pour tous
        mvc.perform(put("/api/trimestres/" + t1 + "/composition").with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"avecComposition\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compositionParCycle.PREMIER.source").value("TRIMESTRE"))
                .andExpect(jsonPath("$.compositionParCycle.SECOND.avecComposition").value(false));

        // réglage propre au second cycle : prioritaire
        mvc.perform(put("/api/trimestres/" + t1 + "/composition/SECOND").with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avecComposition\": true, \"poidsComposition\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compositionParCycle.SECOND.source").value("CYCLE"))
                .andExpect(jsonPath("$.compositionParCycle.SECOND.avecComposition").value(true))
                .andExpect(jsonPath("$.compositionParCycle.SECOND.poidsComposition").value(1.0))
                .andExpect(jsonPath("$.compositionParCycle.PREMIER.avecComposition").value(false));

        // suppression du réglage du cycle : retour au réglage du trimestre
        mvc.perform(delete("/api/trimestres/" + t1 + "/composition/SECOND").with(DIRECTEUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compositionParCycle.SECOND.source").value("TRIMESTRE"));

        // après clôture, le réglage est verrouillé
        mvc.perform(post("/api/trimestres/" + t1 + "/cloture").with(DIRECTEUR))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cloture").value(true));
        mvc.perform(put("/api/trimestres/" + t1 + "/composition").with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"avecComposition\": true}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/trimestres/" + t1 + "/reouverture").with(DIRECTEUR))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cloture").value(false));
    }

    // ---------- Niveaux et classes (SF-04) ----------

    @Test
    void lesClassesSontCreeesParAnneeSansDoublon() throws Exception {
        mvc.perform(get("/api/niveaux").with(ENSEIGNANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[0].code").value("6EME"));
        UUID classe = creerClasse(annee2026, "6EME", "6ème A", 60);
        UUID sixieme = jdbc.queryForObject("SELECT id FROM niveau WHERE code = '6EME'", UUID.class);
        mvc.perform(post("/api/annees-scolaires/" + annee2026 + "/classes").with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"niveauId\": %d, \"nom\": \"6ÈME a\", \"effectifMax\": 50}".formatted(sixieme)))
                .andExpect(status().isConflict());
        // le même nom reste possible une autre année
        creerClasse(annee2025, "6EME", "6ème A", 60);
        mvc.perform(get("/api/annees-scolaires/" + annee2026 + "/classes").with(ENSEIGNANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(classe))
                .andExpect(jsonPath("$[0].niveauCode").value("6EME"))
                .andExpect(jsonPath("$[0].cycle").value("PREMIER"))
                .andExpect(jsonPath("$[0].effectif").value(0));
    }

    // ---------- Personnel (SF-05, SF-06) ----------

    @Test
    void lImportDuPersonnelSeFaitEnDeuxEtapes() throws Exception {
        MockMultipartFile fichier = csv("personnel.csv", """
                Matricule;Nom;Prénom;E-mail;Téléphone;Rôle
                P001;OUEDRAOGO;Awa;awa.ouedraogo@lycee.bf;70000001;Intendant
                P002;SAWADOGO;Issa;issa.sawadogo@lycee.bf;;Professeur
                P003;KABORE;Fatou;fatou.kabore@lycee.bf;70000003;SURVEILLANT
                """);
        mvc.perform(multipart("/api/personnel/import").file(fichier).with(ENSEIGNANT))
                .andExpect(status().isForbidden());

        // Étape 1 : simulation, rien n'est enregistré
        mvc.perform(multipart("/api/personnel/import").file(fichier).with(DIRECTEUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulation").value(true))
                .andExpect(jsonPath("$.importe").value(false))
                .andExpect(jsonPath("$.totalLignes").value(3))
                .andExpect(jsonPath("$.erreurs", hasSize(0)))
                .andExpect(jsonPath("$.lignes[1].role").value("ENSEIGNANT"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM personnel", Integer.class)).isZero();

        // Étape 2 : import définitif
        mvc.perform(multipart("/api/personnel/import").file(fichier).param("simulation", "false").with(DIRECTEUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importe").value(true))
                .andExpect(jsonPath("$.avertissements[0]", containsString("Keycloak")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM personnel", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT role FROM personnel WHERE matricule = 'P002'", String.class))
                .isEqualTo("ENSEIGNANT");
        assertThat(jdbc.queryForObject("SELECT created_by FROM personnel WHERE matricule = 'P001'", String.class))
                .isEqualTo("le.directeur");

        mvc.perform(get("/api/personnel").with(DIRECTEUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].nom").value("KABORE"))
                .andExpect(jsonPath("$[0].compteCree").value(false));
    }

    @Test
    void uneSeuleLigneEnErreurBloqueToutLImportDuPersonnel() throws Exception {
        mvc.perform(post("/api/personnel").with(DIRECTEUR).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"matricule": "P001", "nom": "OUEDRAOGO", "prenom": "Awa",
                                 "email": "awa.ouedraogo@lycee.bf", "role": "INTENDANT"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.personnel.actif").value(true));

        MockMultipartFile fichier = csv("personnel.csv", """
                matricule,nom,prenom,email,role
                P001,OUEDRAOGO,Awa,autre@lycee.bf,INTENDANT
                P010,SAWADOGO,Issa,pas-un-email,ENSEIGNANT
                P011,KABORE,,fatou.kabore@lycee.bf,Jardinier
                P012,ZONGO,Paul,paul.zongo@lycee.bf,CENSEUR
                P012,ZONGO,Pauline,pauline.zongo@lycee.bf,CENSEUR
                """);
        mvc.perform(multipart("/api/personnel/import").file(fichier).param("simulation", "false").with(DIRECTEUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importe").value(false))
                .andExpect(jsonPath("$.erreurs", hasSize(4)))
                .andExpect(jsonPath("$.erreurs[0].ligne").value(2))
                .andExpect(jsonPath("$.erreurs[0].message", containsString("déjà enregistré")))
                .andExpect(jsonPath("$.erreurs[1].message", containsString("email invalide")))
                .andExpect(jsonPath("$.erreurs[2].message", containsString("prénom manquant")))
                .andExpect(jsonPath("$.erreurs[2].message", containsString("rôle inconnu")))
                .andExpect(jsonPath("$.erreurs[3].ligne").value(6))
                .andExpect(jsonPath("$.erreurs[3].message", containsString("en double")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM personnel", Integer.class)).isEqualTo(1);

        // fichier sans la colonne obligatoire « role »
        mvc.perform(multipart("/api/personnel/import")
                        .file(csv("incomplet.csv", "matricule;nom;prenom;email\nP1;A;B;a@b.bf\n")).with(DIRECTEUR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("role")));
        // format non pris en charge
        mvc.perform(multipart("/api/personnel/import")
                        .file(new MockMultipartFile("fichier", "liste.pdf", "application/pdf", new byte[]{1, 2}))
                        .with(DIRECTEUR))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unMembreDuPersonnelPeutEtreDesactive() throws Exception {
        mvc.perform(post("/api/personnel").with(DIRECTEUR).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"matricule": "P001", "nom": "OUEDRAOGO", "prenom": "Awa",
                                 "email": "Awa.Ouedraogo@lycee.bf", "role": "INTENDANT"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.personnel.email").value("awa.ouedraogo@lycee.bf"));
        UUID id = jdbc.queryForObject("SELECT id FROM personnel", UUID.class);
        mvc.perform(post("/api/personnel").with(DIRECTEUR).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"matricule": "P002", "nom": "X", "prenom": "Y",
                                 "email": "awa.ouedraogo@lycee.bf", "role": "ECONOME"}"""))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/personnel/" + id + "/desactivation").with(DIRECTEUR))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(false));
        mvc.perform(put("/api/personnel/" + id).with(DIRECTEUR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\": \"OUEDRAOGO\", \"prenom\": \"Awa Marie\", \"telephone\": \"70112233\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.prenom").value("Awa Marie"));
    }

    // ---------- Élèves et inscriptions (SF-07, SF-08, SF-23) ----------

    @Test
    void lImportDesElevesDeduitLaCategorieNouveauOuAncien() throws Exception {
        UUID sixieme2025 = creerClasse(annee2025, "6EME", "6ème A", 60);
        UUID cinquieme2026 = creerClasse(annee2026, "5EME", "5ème A", 60);

        // Année 2025-2026 : Awa et Issa entrent en 6e
        MockMultipartFile liste2025 = excelEleves(
                new Object[]{"E001", "OUEDRAOGO", "Awa", LocalDate.of(2013, 3, 25), "F", "M. OUEDRAOGO", "70000001", ""},
                new Object[]{"E002", "SAWADOGO", "Issa", LocalDate.of(2012, 11, 2), "M", "Mme SAWADOGO", "70000002", "non"});
        mvc.perform(multipart("/api/classes/" + sixieme2025 + "/inscriptions/import").file(liste2025)
                        .param("simulation", "false").with(SURVEILLANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importe").value(true))
                .andExpect(jsonPath("$.lignes[0].dateNaissance").value("2013-03-25"))
                .andExpect(jsonPath("$.lignes[0].categorie").value("NOUVEAU"))
                .andExpect(jsonPath("$.lignes[0].dejaConnu").value(false));

        // Année 2026-2027 : Awa passe en 5e (ancienne), Fatou arrive (nouvelle)
        MockMultipartFile liste2026 = csv("5eA.csv", """
                Matricule;Nom;Prénom;Date de naissance;Sexe;Redoublant
                E001;OUEDRAOGO;Awa;25/03/2013;F;non
                E003;KABORE;Fatou;07/09/2014;Fille;oui
                """);
        mvc.perform(multipart("/api/classes/" + cinquieme2026 + "/inscriptions/import").file(liste2026)
                        .with(SURVEILLANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulation").value(true))
                .andExpect(jsonPath("$.erreurs", hasSize(0)))
                .andExpect(jsonPath("$.lignes[0].categorie").value("ANCIEN"))
                .andExpect(jsonPath("$.lignes[0].dejaConnu").value(true))
                .andExpect(jsonPath("$.lignes[1].categorie").value("NOUVEAU"))
                .andExpect(jsonPath("$.lignes[1].redoublant").value(true))
                .andExpect(jsonPath("$.avertissements", hasItem(containsString("1 ancien(s)"))));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inscription WHERE classe_id = ?", Integer.class,
                cinquieme2026)).isZero();

        mvc.perform(multipart("/api/classes/" + cinquieme2026 + "/inscriptions/import").file(liste2026)
                        .param("simulation", "false").with(SURVEILLANT))
                .andExpect(status().isOk()).andExpect(jsonPath("$.importe").value(true));

        // La fiche d'Awa n'est pas dupliquée ; ses deux inscriptions sont conservées
        assertThat(jdbc.queryForObject("SELECT count(*) FROM eleve", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM inscription i JOIN eleve e ON e.id = i.eleve_id WHERE e.matricule = 'E001'",
                Integer.class)).isEqualTo(2);
        mvc.perform(get("/api/classes/" + cinquieme2026 + "/inscriptions").with(ENSEIGNANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].eleve.nom").value("KABORE"))
                .andExpect(jsonPath("$[0].categorie").value("NOUVEAU"))
                .andExpect(jsonPath("$[0].redoublant").value(true))
                .andExpect(jsonPath("$[1].categorie").value("ANCIEN"));
        mvc.perform(get("/api/annees-scolaires/" + annee2026 + "/classes").with(ENSEIGNANT))
                .andExpect(jsonPath("$[0].effectif").value(2));

        // Réimporter la même liste : les élèves sont déjà inscrits cette année
        mvc.perform(multipart("/api/classes/" + cinquieme2026 + "/inscriptions/import").file(liste2026)
                        .param("simulation", "false").with(SURVEILLANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importe").value(false))
                .andExpect(jsonPath("$.erreurs[0].message", containsString("déjà inscrit en 5ème A")));

        mvc.perform(get("/api/eleves").param("recherche", "kab").with(ENSEIGNANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].matricule").value("E003"));
    }

    @Test
    void lImportDesElevesRefuseLesLignesInvalidesEtLeDepassementDEffectif() throws Exception {
        UUID classe = creerClasse(annee2026, "6EME", "6ème B", 2);
        mvc.perform(multipart("/api/classes/" + classe + "/inscriptions/import")
                        .file(csv("eleves.csv", "matricule;nom;prenom;date_naissance;sexe\nE1;A;B;2014-01-01;M\n"))
                        .with(ENSEIGNANT))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/classes/" + classe + "/inscriptions/import")
                        .file(csv("eleves.csv", """
                                matricule;nom;prenom;date_naissance;sexe;redoublant
                                E010;ZONGO;Paul;31/02/2014;M;
                                E011;TRAORE;;12/05/2014;X;peut-être
                                E012;DIALLO;Aïcha;12/05/2014;F;
                                """))
                        .param("simulation", "false").with(SURVEILLANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importe").value(false))
                .andExpect(jsonPath("$.erreurs", hasSize(3)))
                .andExpect(jsonPath("$.erreurs[0].message", containsString("date de naissance invalide")))
                .andExpect(jsonPath("$.erreurs[1].message", containsString("prénom manquant")))
                .andExpect(jsonPath("$.erreurs[1].message", containsString("sexe invalide")))
                .andExpect(jsonPath("$.erreurs[1].message", containsString("redoublant")))
                .andExpect(jsonPath("$.erreurs[2].message", containsString("au-delà du maximum de 2")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM eleve", Integer.class)).isZero();
    }

    @Test
    void lInscriptionManuelleEtLaCorrectionDeCategorieSontTracees() throws Exception {
        UUID classe = creerClasse(annee2026, "2NDE_C", "2nde C1", 1);
        String awa = """
                {"matricule": "E001", "nom": "OUEDRAOGO", "prenom": "Awa", "dateNaissance": "2010-03-25",
                 "sexe": "F", "nomParent": "M. OUEDRAOGO", "contactParent": "70000001", "redoublant": false}""";
        mvc.perform(post("/api/classes/" + classe + "/inscriptions").with(INTENDANT)
                .contentType(MediaType.APPLICATION_JSON).content(awa)).andExpect(status().isForbidden());
        mvc.perform(post("/api/classes/" + classe + "/inscriptions").with(SURVEILLANT)
                        .contentType(MediaType.APPLICATION_JSON).content(awa))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categorie").value("NOUVEAU"))
                .andExpect(jsonPath("$.categorieForcee").value(false));
        UUID inscription = jdbc.queryForObject("SELECT id FROM inscription", UUID.class);

        // déjà inscrite, puis classe complète pour un autre élève
        mvc.perform(post("/api/classes/" + classe + "/inscriptions").with(SURVEILLANT)
                        .contentType(MediaType.APPLICATION_JSON).content(awa))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("déjà inscrit")));
        mvc.perform(post("/api/classes/" + classe + "/inscriptions").with(SURVEILLANT)
                        .contentType(MediaType.APPLICATION_JSON).content(awa.replace("E001", "E002")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("complète")));

        // correction de catégorie : réservée à l'intendant, motif obligatoire
        String correction = "{\"categorie\": \"ANCIEN\", \"motif\": \"Élève de retour après un an d'absence\"}";
        mvc.perform(put("/api/inscriptions/" + inscription + "/categorie").with(SURVEILLANT)
                .contentType(MediaType.APPLICATION_JSON).content(correction)).andExpect(status().isForbidden());
        mvc.perform(put("/api/inscriptions/" + inscription + "/categorie").with(INTENDANT)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"categorie\": \"ANCIEN\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/inscriptions/" + inscription + "/categorie").with(INTENDANT)
                        .contentType(MediaType.APPLICATION_JSON).content(correction))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorie").value("ANCIEN"))
                .andExpect(jsonPath("$.categorieForcee").value(true));

        // la piste d'audit conserve l'ancienne et la nouvelle valeur, avec l'auteur
        assertThat(jdbc.queryForList("""
                SELECT a.categorie || ' par ' || r.username FROM audit.inscription_aud a
                JOIN audit.revinfo r ON r.rev_id = a.rev_id WHERE a.id = ? ORDER BY a.rev_id""", String.class, inscription))
                .containsExactly("NOUVEAU par le.surveillant", "ANCIEN par l.intendant");
    }
}
