package bf.lycee.gestion;

import java.util.UUID;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Lot 4 : évaluations, saisie des notes, moyennes, classement et bulletins. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotesIntegrationTest {

    private static final RequestPostProcessor DIRECTEUR = utilisateur("le.directeur", "DIRECTEUR");
    private static final RequestPostProcessor ENSEIGNANT = utilisateur("prof.math", "ENSEIGNANT");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID annee;
    private UUID classe;
    private UUID trimestre;
    private UUID maths;
    private UUID francais;
    private UUID awa;
    private UUID salif;

    @BeforeEach
    void preparer() throws Exception {
        jdbc.execute("TRUNCATE TABLE annee_scolaire, personnel, eleve, matiere, audit.revinfo "
                + "RESTART IDENTITY CASCADE");
        annee = creerAnnee();
        classe = creerClasse("6EME", "6ème A");
        trimestre = creerTrimestre(1, "2026-10-01", "2026-12-20");
        maths = creerEnseignement(creerMatiere("MATH", "Mathématiques"), 4);
        francais = creerEnseignement(creerMatiere("FR", "Français"), 2);
        awa = inscrire("EL-001", "OUEDRAOGO", "Awa");
        salif = inscrire("EL-002", "KABORE", "Salif");
    }

    // ---------- Évaluations (SF-11) ----------

    @Test
    void une_seule_composition_par_matiere_et_par_trimestre() throws Exception {
        creerEvaluation(maths, "COMPOSITION", "Composition");
        mvc.perform(post(evaluationsDe(maths)).with(DIRECTEUR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categorie\": \"COMPOSITION\", \"libelle\": \"Composition bis\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("composition existe déjà")));
    }

    @Test
    void la_composition_est_refusee_quand_l_option_est_desactivee() throws Exception {
        reglerComposition(false, null);
        mvc.perform(post(evaluationsDe(maths)).with(DIRECTEUR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categorie\": \"COMPOSITION\", \"libelle\": \"Composition\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("n'est pas activée")));
    }

    @Test
    void un_enseignant_ne_saisit_pas_dans_une_matiere_dont_il_n_est_pas_titulaire() throws Exception {
        mvc.perform(post(evaluationsDe(maths)).with(ENSEIGNANT).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categorie\": \"DEVOIR\", \"libelle\": \"Devoir 1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("professeur titulaire")));
    }

    @Test
    void les_notes_ne_sont_plus_modifiables_apres_la_cloture_du_trimestre() throws Exception {
        UUID devoir = creerEvaluation(maths, "DEVOIR", "Devoir 1");
        mvc.perform(post("/api/trimestres/" + trimestre + "/cloture").with(DIRECTEUR))
                .andExpect(status().isOk());

        mvc.perform(put(grilleDe(maths)).with(DIRECTEUR).contentType(MediaType.APPLICATION_JSON)
                        .content(note(awa, devoir, "12", false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("clôturé")));
    }

    // ---------- Moyenne par matière (SF-13) ----------

    @Test
    void sans_composition_la_moyenne_est_celle_des_devoirs() throws Exception {
        reglerComposition(false, null);
        UUID devoir1 = creerEvaluation(maths, "DEVOIR", "Devoir 1");
        UUID devoir2 = creerEvaluation(maths, "DEVOIR", "Devoir 2");

        saisir(maths, note(awa, devoir1, "12", false), note(awa, devoir2, "16", false));

        mvc.perform(get(grilleDe(maths)).with(DIRECTEUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avecComposition").value(false))
                .andExpect(jsonPath("$.lignes[?(@.matricule == 'EL-001')].moyenne").value(14.00));
    }

    @Test
    void avec_composition_la_note_de_composition_pese_son_poids() throws Exception {
        reglerComposition(true, "2");
        UUID devoir1 = creerEvaluation(maths, "DEVOIR", "Devoir 1");
        UUID devoir2 = creerEvaluation(maths, "DEVOIR", "Devoir 2");
        UUID composition = creerEvaluation(maths, "COMPOSITION", "Composition");

        // Devoirs 12 et 16 (moyenne 14), composition 17 : (14 + 2 × 17) / 3 = 16,00
        saisir(maths, note(awa, devoir1, "12", false), note(awa, devoir2, "16", false),
                note(awa, composition, "17", false));

        mvc.perform(get(grilleDe(maths)).with(DIRECTEUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes[?(@.matricule == 'EL-001')].moyenne").value(16.00));
    }

    @Test
    void une_absence_est_exclue_du_calcul_et_signalee_pour_la_composition() throws Exception {
        reglerComposition(true, "2");
        UUID devoir1 = creerEvaluation(maths, "DEVOIR", "Devoir 1");
        UUID devoir2 = creerEvaluation(maths, "DEVOIR", "Devoir 2");
        creerEvaluation(maths, "COMPOSITION", "Composition");

        // Absent au second devoir : la moyenne ne retient que le premier.
        saisir(maths, note(awa, devoir1, "12", false), note(awa, devoir2, null, true));

        mvc.perform(get(grilleDe(maths)).with(DIRECTEUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes[?(@.matricule == 'EL-001')].moyenne").value(12.00));
    }

    @Test
    void sans_aucune_note_la_matiere_est_non_classee() throws Exception {
        mvc.perform(get("/api/inscriptions/" + awa + "/trimestres/" + trimestre + "/bulletin")
                        .with(DIRECTEUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes[0].nonClasse").value(true))
                .andExpect(jsonPath("$.moyenneGenerale").doesNotExist());
    }

    // ---------- Moyenne générale, classement et bulletin (SF-14 à SF-18) ----------

    @Test
    void la_moyenne_generale_est_ponderee_par_les_coefficients() throws Exception {
        reglerComposition(false, null);
        UUID devoirMaths = creerEvaluation(maths, "DEVOIR", "Devoir 1");
        UUID devoirFrancais = creerEvaluation(francais, "DEVOIR", "Devoir 1");

        saisir(maths, note(awa, devoirMaths, "14", false));
        saisir(francais, note(awa, devoirFrancais, "10", false));

        // (14 × 4 + 10 × 2) / 6 = 12,67
        mvc.perform(get("/api/inscriptions/" + awa + "/trimestres/" + trimestre + "/bulletin")
                        .with(DIRECTEUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCoefficients").value(6.0))
                .andExpect(jsonPath("$.moyenneGenerale").value(12.67))
                .andExpect(jsonPath("$.rang").value(1))
                .andExpect(jsonPath("$.statistiques.effectif").value(2));
    }

    @Test
    void les_eleves_a_egalite_partagent_le_meme_rang() throws Exception {
        reglerComposition(false, null);
        UUID devoir = creerEvaluation(maths, "DEVOIR", "Devoir 1");
        UUID troisieme = inscrire("EL-003", "ZONGO", "Ali");

        saisir(maths, note(awa, devoir, "15", false), note(salif, devoir, "15", false),
                note(troisieme, devoir, "9", false));

        mvc.perform(get("/api/classes/" + classe + "/trimestres/" + trimestre + "/classement")
                        .with(DIRECTEUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rang").value(1))
                .andExpect(jsonPath("$[0].exAequo").value(true))
                .andExpect(jsonPath("$[1].rang").value(1))
                .andExpect(jsonPath("$[2].rang").value(3))
                .andExpect(jsonPath("$[2].exAequo").value(false));
    }

    @Test
    void la_deliberation_fige_les_resultats_et_le_conseil_decide_de_la_mention() throws Exception {
        reglerComposition(false, null);
        UUID devoir = creerEvaluation(maths, "DEVOIR", "Devoir 1");
        saisir(maths, note(awa, devoir, "17", false));

        mvc.perform(post("/api/classes/" + classe + "/trimestres/" + trimestre + "/deliberation")
                        .with(DIRECTEUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mention").value("Félicitations"));

        mvc.perform(put("/api/inscriptions/" + awa + "/trimestres/" + trimestre + "/decision")
                        .with(DIRECTEUR).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mention": "Tableau d'honneur",
                                 "appreciationConseil": "Trimestre très satisfaisant."}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mention").value("Tableau d'honneur"))
                .andExpect(jsonPath("$.appreciationConseil").value("Trimestre très satisfaisant."));
    }

    @Test
    void la_decision_exige_une_deliberation_prealable() throws Exception {
        mvc.perform(put("/api/inscriptions/" + awa + "/trimestres/" + trimestre + "/decision")
                        .with(DIRECTEUR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mention\": \"Encouragements\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("délibération")));
    }

    @Test
    void une_epreuve_verrouillee_n_accepte_plus_aucune_note() throws Exception {
        reglerComposition(false, null);
        UUID devoir = creerEvaluation(maths, "DEVOIR", "Devoir 1");
        saisir(maths, note(awa, devoir, "12", false));

        mvc.perform(put("/api/evaluations/" + devoir + "/verrou").with(ENSEIGNANT)
                        .param("verrouillee", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verrouillee").value(true));

        // Plus de correction possible, ni d'ajout de note, ni de suppression.
        mvc.perform(put(grilleDe(maths)).with(DIRECTEUR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": [%s]}".formatted(note(awa, devoir, "18", false))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(delete("/api/evaluations/" + devoir).with(DIRECTEUR))
                .andExpect(status().isUnprocessableEntity());

        // La note d'avant le verrou reste celle qui compte.
        mvc.perform(get(grilleDe(maths)).with(DIRECTEUR))
                .andExpect(jsonPath("$.evaluations[0].verrouillee").value(true))
                .andExpect(jsonPath("$.lignes[?(@.matricule == 'EL-001')].moyenne").value(12.00));
    }

    @Test
    void un_enseignant_ne_peut_pas_rouvrir_une_epreuve_qu_il_a_verrouillee() throws Exception {
        reglerComposition(false, null);
        UUID devoir = creerEvaluation(maths, "DEVOIR", "Devoir 1");
        mvc.perform(put("/api/evaluations/" + devoir + "/verrou").with(ENSEIGNANT)
                        .param("verrouillee", "true"))
                .andExpect(status().isOk());

        mvc.perform(put("/api/evaluations/" + devoir + "/verrou").with(ENSEIGNANT)
                        .param("verrouillee", "false"))
                .andExpect(status().isUnprocessableEntity());

        // La direction, elle, peut rouvrir : la saisie redevient possible.
        mvc.perform(put("/api/evaluations/" + devoir + "/verrou").with(DIRECTEUR)
                        .param("verrouillee", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verrouillee").value(false));
        saisir(maths, note(awa, devoir, "15", false));
    }

    // ---------- Outils ----------

    private static RequestPostProcessor utilisateur(String nom, String role) {
        return jwt()
                .jwt(jeton -> jeton.subject("id-" + nom).claim("preferred_username", nom))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private String evaluationsDe(UUID enseignement) {
        return "/api/enseignements/" + enseignement + "/trimestres/" + trimestre + "/evaluations";
    }

    private String grilleDe(UUID enseignement) {
        return "/api/enseignements/" + enseignement + "/trimestres/" + trimestre + "/grille";
    }

    private UUID creerAnnee() throws Exception {
        mvc.perform(post("/api/annees-scolaires").with(DIRECTEUR).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle": "2026-2027", "dateDebut": "2026-10-01",
                                 "dateFin": "2027-07-15"}"""))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM annee_scolaire WHERE libelle = '2026-2027'", UUID.class);
    }

    private UUID creerClasse(String niveau, String nom) throws Exception {
        UUID niveauId = jdbc.queryForObject("SELECT id FROM niveau WHERE code = ?", UUID.class, niveau);
        mvc.perform(post("/api/annees-scolaires/" + annee + "/classes").with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"niveauId\": \"%s\", \"nom\": \"%s\", \"effectifMax\": 60}"
                                .formatted(niveauId, nom)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM classe WHERE nom = ?", UUID.class, nom);
    }

    private UUID creerTrimestre(int numero, String debut, String fin) throws Exception {
        mvc.perform(post("/api/annees-scolaires/" + annee + "/trimestres").with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\": %d, \"dateDebut\": \"%s\", \"dateFin\": \"%s\"}"
                                .formatted(numero, debut, fin)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM trimestre WHERE numero = ?", UUID.class, numero);
    }

    private UUID creerMatiere(String code, String libelle) throws Exception {
        mvc.perform(post("/api/matieres").with(DIRECTEUR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"%s\", \"libelle\": \"%s\"}".formatted(code, libelle)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM matiere WHERE code = ?", UUID.class, code);
    }

    private UUID creerEnseignement(UUID matiereId, int coefficient) throws Exception {
        mvc.perform(post("/api/classes/" + classe + "/enseignements").with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matiereId\": \"%s\", \"coefficient\": %d}"
                                .formatted(matiereId, coefficient)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject(
                "SELECT id FROM enseignement WHERE classe_id = ? AND matiere_id = ?",
                UUID.class, classe, matiereId);
    }

    private UUID inscrire(String matricule, String nom, String prenom) throws Exception {
        mvc.perform(post("/api/classes/" + classe + "/inscriptions").with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"matricule": "%s", "nom": "%s", "prenom": "%s",
                                 "dateNaissance": "2012-03-15", "sexe": "M"}"""
                                .formatted(matricule, nom, prenom)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("""
                SELECT i.id FROM inscription i JOIN eleve e ON e.id = i.eleve_id
                WHERE e.matricule = ?""", UUID.class, matricule);
    }

    private UUID creerEvaluation(UUID enseignement, String categorie, String libelle) throws Exception {
        mvc.perform(post(evaluationsDe(enseignement)).with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categorie\": \"%s\", \"libelle\": \"%s\"}"
                                .formatted(categorie, libelle)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("""
                SELECT id FROM evaluation WHERE enseignement_id = ? AND libelle = ?""",
                UUID.class, enseignement, libelle);
    }

    private void reglerComposition(boolean avec, String poids) throws Exception {
        mvc.perform(put("/api/trimestres/" + trimestre + "/composition").with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avecComposition\": %s, \"poidsComposition\": %s}"
                                .formatted(avec, poids == null ? "null" : poids)))
                .andExpect(status().isOk());
    }

    private static String note(UUID inscription, UUID evaluation, String valeur, boolean absent) {
        return """
                {"inscriptionId": "%s", "evaluationId": "%s", "valeur": %s, "absent": %s}"""
                .formatted(inscription, evaluation, valeur == null ? "null" : valeur, absent);
    }

    private void saisir(UUID enseignement, String... notes) throws Exception {
        mvc.perform(put(grilleDe(enseignement)).with(DIRECTEUR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": [%s]}".formatted(String.join(",", notes))))
                .andExpect(status().isOk());
    }
}
