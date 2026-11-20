package bf.lycee.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
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

/**
 * Programme d'un niveau, affectation des professeurs aux matières d'une classe
 * et modification des fiches du personnel (SF-09, SF-10).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProgrammeIntegrationTest {

    private static final RequestPostProcessor DIRECTEUR = utilisateur("le.directeur", "DIRECTEUR");
    private static final RequestPostProcessor ENSEIGNANT = utilisateur("prof", "ENSEIGNANT");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID annee;
    private UUID niveau6;
    private UUID maths;
    private UUID francais;

    @BeforeEach
    void preparer() throws Exception {
        jdbc.execute("TRUNCATE TABLE annee_scolaire, personnel, eleve, matiere, audit.revinfo "
                + "RESTART IDENTITY CASCADE");
        mvc.perform(post("/api/annees-scolaires").with(DIRECTEUR).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle": "2026-2027", "dateDebut": "2026-10-01",
                                 "dateFin": "2027-07-15"}"""))
                .andExpect(status().isCreated());
        annee = jdbc.queryForObject("SELECT id FROM annee_scolaire", UUID.class);
        niveau6 = jdbc.queryForObject("SELECT id FROM niveau WHERE code = '6EME'", UUID.class);
        maths = creerMatiere("MATH", "Mathématiques");
        francais = creerMatiere("FR", "Français");
    }

    // ---------- Programme du niveau (SF-09) ----------

    @Test
    void le_programme_du_niveau_porte_les_matieres_et_leurs_coefficients() throws Exception {
        inscrireAuProgramme(niveau6, maths, 4);
        inscrireAuProgramme(niveau6, francais, 5);

        mvc.perform(get("/api/annees-scolaires/" + annee + "/niveaux/" + niveau6 + "/programme").with(DIRECTEUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].matiereLibelle").value("Français"))
                .andExpect(jsonPath("$[0].coefficient").value(5.0))
                .andExpect(jsonPath("$[1].matiereLibelle").value("Mathématiques"));
    }

    @Test
    void une_matiere_ne_figure_qu_une_fois_au_programme() throws Exception {
        inscrireAuProgramme(niveau6, maths, 4);
        mvc.perform(post("/api/annees-scolaires/" + annee + "/niveaux/" + niveau6 + "/programme").with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matiereId\": \"%s\", \"coefficient\": 3}".formatted(maths)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("figure déjà au programme")));
    }

    @Test
    void une_classe_creee_recoit_les_matieres_de_son_niveau() throws Exception {
        inscrireAuProgramme(niveau6, maths, 4);
        inscrireAuProgramme(niveau6, francais, 5);

        UUID classe = creerClasse("6ème A");

        mvc.perform(get("/api/classes/" + classe + "/enseignements").with(DIRECTEUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].matiereCode").value("FR"))
                .andExpect(jsonPath("$[0].coefficient").value(5.0))
                .andExpect(jsonPath("$[1].matiereCode").value("MATH"))
                .andExpect(jsonPath("$[0].enseignantId").doesNotExist());
    }

    @Test
    void appliquer_le_programme_complete_les_classes_existantes() throws Exception {
        UUID classeA = creerClasse("6ème A");
        UUID classeB = creerClasse("6ème B");
        assertThat(nombreEnseignements(classeA)).isZero();

        inscrireAuProgramme(niveau6, maths, 4);
        inscrireAuProgramme(niveau6, francais, 5);

        mvc.perform(post("/api/annees-scolaires/" + annee + "/niveaux/" + niveau6 + "/programme/application").with(DIRECTEUR)
                        .param("anneeId", annee.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classesTouchees").value(2))
                .andExpect(jsonPath("$.matieresAjoutees").value(4))
                .andExpect(jsonPath("$.details[0]").value(containsString("6ème A")));

        assertThat(nombreEnseignements(classeA)).isEqualTo(2);
        assertThat(nombreEnseignements(classeB)).isEqualTo(2);
    }

    @Test
    void appliquer_le_programme_respecte_les_professeurs_et_n_aligne_les_coefficients_que_sur_demande()
            throws Exception {
        inscrireAuProgramme(niveau6, maths, 4);
        UUID classe = creerClasse("6ème A");
        UUID professeur = creerPersonnel("P-001", "ZONGO", "Rasmané", "r.zongo@lycee.bf", "ENSEIGNANT");
        UUID enseignement = jdbc.queryForObject("SELECT id FROM enseignement", UUID.class);

        affecter(classe, enseignement, professeur);
        // Le coefficient de la classe est ajusté à la main, puis le programme change.
        mvc.perform(put("/api/enseignements/" + enseignement).with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matiereId\": \"%s\", \"enseignantId\": \"%s\", \"coefficient\": 6}"
                                .formatted(maths, professeur)))
                .andExpect(status().isOk());

        // Sans demande d'alignement, le coefficient de la classe est conservé.
        mvc.perform(post("/api/annees-scolaires/" + annee + "/niveaux/" + niveau6 + "/programme/application").with(DIRECTEUR)
                        .param("anneeId", annee.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coefficientsMisAJour").value(0));
        mvc.perform(get("/api/classes/" + classe + "/enseignements").with(DIRECTEUR))
                .andExpect(jsonPath("$[0].coefficient").value(6.0))
                .andExpect(jsonPath("$[0].enseignantNom").value("ZONGO Rasmané"));

        // Avec alignement, il revient à la valeur du programme, le professeur restant en place.
        mvc.perform(post("/api/annees-scolaires/" + annee + "/niveaux/" + niveau6 + "/programme/application").with(DIRECTEUR)
                        .param("anneeId", annee.toString())
                        .param("alignerCoefficients", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coefficientsMisAJour").value(1));
        mvc.perform(get("/api/classes/" + classe + "/enseignements").with(DIRECTEUR))
                .andExpect(jsonPath("$[0].coefficient").value(4.0))
                .andExpect(jsonPath("$[0].enseignantNom").value("ZONGO Rasmané"));
    }

    @Test
    void une_matiere_au_programme_ne_peut_pas_etre_supprimee() throws Exception {
        inscrireAuProgramme(niveau6, maths, 4);
        mvc.perform(delete("/api/matieres/" + maths).with(DIRECTEUR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("programme")));
    }

    // ---------- Affectation des professeurs (SF-10) ----------

    @Test
    void les_professeurs_sont_affectes_en_une_fois_et_leur_service_est_consultable() throws Exception {
        inscrireAuProgramme(niveau6, maths, 4);
        inscrireAuProgramme(niveau6, francais, 5);
        UUID classeA = creerClasse("6ème A");
        UUID classeB = creerClasse("6ème B");
        UUID professeur = creerPersonnel("P-001", "ZONGO", "Rasmané", "r.zongo@lycee.bf", "ENSEIGNANT");

        var mathsA = jdbc.queryForObject("""
                SELECT e.id FROM enseignement e JOIN matiere m ON m.id = e.matiere_id
                WHERE e.classe_id = ? AND m.code = 'MATH'""", UUID.class, classeA);
        var mathsB = jdbc.queryForObject("""
                SELECT e.id FROM enseignement e JOIN matiere m ON m.id = e.matiere_id
                WHERE e.classe_id = ? AND m.code = 'MATH'""", UUID.class, classeB);

        affecter(classeA, mathsA, professeur);
        affecter(classeB, mathsB, professeur);

        mvc.perform(get("/api/personnel/" + professeur + "/charge").with(DIRECTEUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enseignantNom").value("ZONGO Rasmané"))
                .andExpect(jsonPath("$.nombreClasses").value(2))
                .andExpect(jsonPath("$.totalCoefficients").value(8.0));
    }

    @Test
    void une_fiche_desactivee_ne_peut_plus_etre_affectee() throws Exception {
        inscrireAuProgramme(niveau6, maths, 4);
        UUID classe = creerClasse("6ème A");
        UUID professeur = creerPersonnel("P-001", "ZONGO", "Rasmané", "r.zongo@lycee.bf", "ENSEIGNANT");
        UUID enseignement = jdbc.queryForObject("SELECT id FROM enseignement", UUID.class);

        mvc.perform(post("/api/personnel/" + professeur + "/desactivation").with(DIRECTEUR))
                .andExpect(status().isOk());

        mvc.perform(put("/api/classes/" + classe + "/affectations").with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"affectations": [{"enseignementId": "%s", "enseignantId": "%s"}]}"""
                                .formatted(enseignement, professeur)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("désactivée")));
    }

    @Test
    void un_enseignant_ne_touche_pas_au_programme() throws Exception {
        mvc.perform(post("/api/annees-scolaires/" + annee + "/niveaux/" + niveau6 + "/programme").with(ENSEIGNANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matiereId\": \"%s\", \"coefficient\": 4}".formatted(maths)))
                .andExpect(status().isForbidden());
    }

    // ---------- Fiches du personnel ----------

    @Test
    void la_fiche_du_personnel_se_modifie_role_compris() throws Exception {
        UUID personnel = creerPersonnel("P-001", "ZONGO", "Rasmané", "r.zongo@lycee.bf", "ENSEIGNANT");

        mvc.perform(put("/api/personnel/" + personnel).with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nom": "ZONGO", "prenom": "Rasmané Paul",
                                 "email": "rasmane.zongo@lycee.bf", "telephone": "70 11 22 33",
                                 "role": "CENSEUR"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prenom").value("Rasmané Paul"))
                .andExpect(jsonPath("$.email").value("rasmane.zongo@lycee.bf"))
                .andExpect(jsonPath("$.role").value("CENSEUR"));
    }

    @Test
    void deux_fiches_ne_partagent_pas_une_adresse() throws Exception {
        creerPersonnel("P-001", "ZONGO", "Rasmané", "r.zongo@lycee.bf", "ENSEIGNANT");
        UUID autre = creerPersonnel("P-002", "SORÉ", "Ali", "a.sore@lycee.bf", "ENSEIGNANT");

        mvc.perform(put("/api/personnel/" + autre).with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nom": "SORÉ", "prenom": "Ali", "email": "r.zongo@lycee.bf",
                                 "role": "ENSEIGNANT"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("porte déjà l'adresse")));
    }

    // ---------- Outils ----------

    private static RequestPostProcessor utilisateur(String nom, String role) {
        return jwt()
                .jwt(jeton -> jeton.subject("id-" + nom).claim("preferred_username", nom))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private UUID creerMatiere(String code, String libelle) throws Exception {
        mvc.perform(post("/api/matieres").with(DIRECTEUR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"%s\", \"libelle\": \"%s\"}".formatted(code, libelle)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM matiere WHERE code = ?", UUID.class, code);
    }

    private void inscrireAuProgramme(UUID niveau, UUID matiere, int coefficient) throws Exception {
        mvc.perform(post("/api/annees-scolaires/" + annee + "/niveaux/" + niveau + "/programme").with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matiereId\": \"%s\", \"coefficient\": %d}"
                                .formatted(matiere, coefficient)))
                .andExpect(status().isCreated());
    }

    private UUID creerClasse(String nom) throws Exception {
        mvc.perform(post("/api/annees-scolaires/" + annee + "/classes").with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"niveauId\": \"%s\", \"nom\": \"%s\", \"effectifMax\": 60}"
                                .formatted(niveau6, nom)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM classe WHERE nom = ?", UUID.class, nom);
    }

    private UUID creerPersonnel(String matricule, String nom, String prenom, String email, String role)
            throws Exception {
        mvc.perform(post("/api/personnel").with(DIRECTEUR).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"matricule": "%s", "nom": "%s", "prenom": "%s", "email": "%s",
                                 "role": "%s"}""".formatted(matricule, nom, prenom, email, role)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM personnel WHERE matricule = ?", UUID.class, matricule);
    }

    private void affecter(UUID classe, UUID enseignement, UUID professeur) throws Exception {
        mvc.perform(put("/api/classes/" + classe + "/affectations").with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"affectations": [{"enseignementId": "%s", "enseignantId": "%s"}]}"""
                                .formatted(enseignement, professeur)))
                .andExpect(status().isOk());
    }

    private int nombreEnseignements(UUID classe) {
        return jdbc.queryForObject("SELECT count(*) FROM enseignement WHERE classe_id = ?",
                Integer.class, classe);
    }
}
