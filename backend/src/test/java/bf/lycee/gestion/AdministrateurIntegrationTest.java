package bf.lycee.gestion;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
 * Le rôle ADMIN ouvre toutes les portes : ce qui demande d'ordinaire la
 * direction, l'intendance, la caisse ou le professeur titulaire.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdministrateurIntegrationTest {

    private static final RequestPostProcessor ADMIN = jwt()
            .jwt(jeton -> jeton.subject("id-admin").claim("preferred_username", "admin"))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID annee;
    private UUID classe;
    private UUID inscription;

    @BeforeEach
    void preparer() throws Exception {
        jdbc.execute("TRUNCATE TABLE annee_scolaire, personnel, eleve, matiere, audit.revinfo "
                + "RESTART IDENTITY CASCADE");

        // Création d'une année scolaire : réservée à la direction.
        mvc.perform(post("/api/annees-scolaires").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle": "2026-2027", "dateDebut": "2026-10-01",
                                 "dateFin": "2027-07-15"}"""))
                .andExpect(status().isCreated());
        annee = jdbc.queryForObject("SELECT id FROM annee_scolaire WHERE libelle = '2026-2027'",
                UUID.class);

        UUID niveau = jdbc.queryForObject("SELECT id FROM niveau WHERE code = '6EME'", UUID.class);
        mvc.perform(post("/api/annees-scolaires/" + annee + "/classes").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"niveauId\": \"%s\", \"nom\": \"6ème A\", \"effectifMax\": 60}"
                                .formatted(niveau)))
                .andExpect(status().isCreated());
        classe = jdbc.queryForObject("SELECT id FROM classe WHERE nom = '6ème A'", UUID.class);

        // Inscription d'un élève : réservée à la scolarité.
        mvc.perform(post("/api/classes/" + classe + "/inscriptions").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"matricule": "EL-001", "nom": "OUEDRAOGO", "prenom": "Awa",
                                 "dateNaissance": "2012-03-15", "sexe": "F"}"""))
                .andExpect(status().isCreated());
        inscription = jdbc.queryForObject("SELECT id FROM inscription", UUID.class);
    }

    @Test
    void il_parametre_les_frais_et_encaisse() throws Exception {
        UUID inscriptionFrais = jdbc.queryForObject(
                "SELECT id FROM type_frais WHERE code = 'INSCRIPTION'", UUID.class);
        UUID niveau = jdbc.queryForObject("SELECT id FROM niveau WHERE code = '6EME'", UUID.class);

        // Grille tarifaire : réservée à l'intendance.
        mvc.perform(post("/api/annees-scolaires/" + annee + "/grille-tarifaire").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"typeFraisId": "%s", "niveauId": "%s", "categorie": "TOUS",
                                 "montant": 15000}""".formatted(inscriptionFrais, niveau)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/annees-scolaires/" + annee + "/plans-paiement").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle": "Plan annuel", "modeRepartition": "POURCENTAGE",
                                 "parDefaut": true, "tranches": [
                                   {"numero": 1, "libelle": "Tranche unique",
                                    "dateEcheance": "2026-10-15", "pourcentage": 100}]}"""))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/inscriptions/" + inscription + "/finances/generation").with(ADMIN))
                .andExpect(status().isOk());

        // Encaissement : réservé à la caisse.
        mvc.perform(post("/api/inscriptions/" + inscription + "/paiements").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"datePaiement": "%s", "montant": 5000, "mode": "ESPECES"}"""
                                .formatted(LocalDate.now())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroRecu").value(containsString("REC-")));

        // Consultation réservée aux rôles financiers.
        mvc.perform(get("/api/annees-scolaires/" + annee + "/impayes").with(ADMIN))
                .andExpect(status().isOk());
    }

    @Test
    void il_accorde_une_remise_que_seul_le_directeur_peut_valider() throws Exception {
        UUID scolarite = jdbc.queryForObject(
                "SELECT id FROM type_frais WHERE code = 'SCOLARITE'", UUID.class);
        UUID niveau = jdbc.queryForObject("SELECT id FROM niveau WHERE code = '6EME'", UUID.class);
        mvc.perform(post("/api/annees-scolaires/" + annee + "/grille-tarifaire").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"typeFraisId": "%s", "niveauId": "%s", "categorie": "TOUS",
                                 "montant": 90000}""".formatted(scolarite, niveau)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/inscriptions/" + inscription + "/finances/generation").with(ADMIN))
                .andExpect(status().isOk());

        UUID frais = jdbc.queryForObject("""
                SELECT f.id FROM frais_eleve f JOIN type_frais t ON t.id = f.type_frais_id
                WHERE t.code = 'SCOLARITE'""", UUID.class);

        // 40 000 FCFA dépassent le seuil : seul le Directeur — ou l'administrateur — valide.
        mvc.perform(put("/api/frais-eleve/" + frais + "/remise").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "MONTANT", "valeur": 40000, "motif": "Enfant du personnel"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.montantNet").value(50000))
                .andExpect(jsonPath("$.remiseValideePar").value("admin"));
    }

    @Test
    void il_saisit_des_notes_sans_etre_professeur_titulaire() throws Exception {
        mvc.perform(post("/api/matieres").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"MATH\", \"libelle\": \"Mathématiques\"}"))
                .andExpect(status().isCreated());
        UUID matiere = jdbc.queryForObject("SELECT id FROM matiere WHERE code = 'MATH'", UUID.class);

        mvc.perform(post("/api/classes/" + classe + "/enseignements").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matiereId\": \"%s\", \"coefficient\": 4}".formatted(matiere)))
                .andExpect(status().isCreated());
        UUID enseignement = jdbc.queryForObject("SELECT id FROM enseignement", UUID.class);

        mvc.perform(post("/api/annees-scolaires/" + annee + "/trimestres").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numero": 1, "dateDebut": "2026-10-01", "dateFin": "2026-12-20"}"""))
                .andExpect(status().isCreated());
        UUID trimestre = jdbc.queryForObject("SELECT id FROM trimestre", UUID.class);

        // Aucun professeur n'est titulaire de cette matière : un enseignant serait refusé.
        mvc.perform(post("/api/enseignements/" + enseignement + "/trimestres/" + trimestre
                        + "/evaluations").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categorie\": \"DEVOIR\", \"libelle\": \"Devoir 1\"}"))
                .andExpect(status().isCreated());
        UUID evaluation = jdbc.queryForObject("SELECT id FROM evaluation", UUID.class);

        mvc.perform(put("/api/enseignements/" + enseignement + "/trimestres/" + trimestre
                        + "/grille").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"notes": [{"inscriptionId": "%s", "evaluationId": "%s",
                                 "valeur": 14, "absent": false}]}"""
                                .formatted(inscription, evaluation)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes[0].moyenne").value(14.00));

        mvc.perform(post("/api/classes/" + classe + "/trimestres/" + trimestre + "/deliberation")
                        .with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rang").value(1));
    }

    @Test
    void il_gere_le_personnel() throws Exception {
        mvc.perform(post("/api/personnel").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"matricule": "P-100", "nom": "SAWADOGO", "prenom": "Issa",
                                 "email": "i.sawadogo@lycee.bf", "role": "ENSEIGNANT"}"""))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/personnel").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu[0].matricule").value("P-100"))
                .andExpect(jsonPath("$.total").value(1));
    }
}
