package bf.lycee.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bf.lycee.gestion.repository.AnneeScolaireRepository;
import org.flywaydb.core.Flyway;
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
 * Tests du socle : le démarrage valide la correspondance entités / schéma
 * Flyway (ddl-auto=validate), puis on vérifie la sécurité par rôle et l'audit.
 * Nécessite une base PostgreSQL « lycee_test » (voir docker-compose).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SocleIntegrationTest {

    private static final String ANNEE_2026 = """
            {"libelle": "2026-2027", "dateDebut": "2026-10-01", "dateFin": "2027-07-15"}
            """;
    private static final String ANNEE_2027 = """
            {"libelle": "2027-2028", "dateDebut": "2027-10-01", "dateFin": "2028-07-15"}
            """;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AnneeScolaireRepository annees;
    @Autowired
    private Flyway flyway;

    @BeforeEach
    void viderLesDonnees() {
        jdbc.execute("TRUNCATE TABLE annee_scolaire_aud, annee_scolaire RESTART IDENTITY CASCADE");
    }

    private static RequestPostProcessor utilisateur(String nom, String role) {
        return jwt()
                .jwt(jeton -> jeton.subject("id-" + nom).claim("preferred_username", nom))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void leSchemaComporteLes22TablesMetiersEtLeursTablesDAudit() {
        Integer metiers = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = current_schema() AND table_name NOT LIKE '%\\_aud'
                  AND table_name NOT IN ('revinfo', 'flyway_schema_history')""", Integer.class);
        Integer audits = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = current_schema() AND table_name LIKE '%\\_aud'""", Integer.class);
        assertThat(metiers).isEqualTo(22);
        assertThat(audits).isEqualTo(22);
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void sansJetonLAccesEstRefuse() throws Exception {
        mvc.perform(get("/api/annees-scolaires")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/moi")).andExpect(status().isUnauthorized());
    }

    @Test
    void leProfilRenvoieLeNomEtLesRoles() throws Exception {
        mvc.perform(get("/api/moi").with(utilisateur("awa.intendante", "INTENDANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomUtilisateur").value("awa.intendante"))
                .andExpect(jsonPath("$.roles", contains("INTENDANT")));
    }

    @Test
    void unEnseignantPeutConsulterMaisPasCreerUneAnnee() throws Exception {
        mvc.perform(get("/api/annees-scolaires").with(utilisateur("prof.math", "ENSEIGNANT")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/annees-scolaires").with(utilisateur("prof.math", "ENSEIGNANT"))
                        .contentType(MediaType.APPLICATION_JSON).content(ANNEE_2026))
                .andExpect(status().isForbidden());
        assertThat(annees.count()).isZero();
    }

    @Test
    void leDirecteurCreeUneAnneeEtLAuditEnregistreSonNom() throws Exception {
        mvc.perform(post("/api/annees-scolaires").with(utilisateur("le.directeur", "DIRECTEUR"))
                        .contentType(MediaType.APPLICATION_JSON).content(ANNEE_2026))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.libelle").value("2026-2027"))
                .andExpect(jsonPath("$.actif").value(false))
                .andExpect(jsonPath("$.avecCompositionDefaut").value(true))
                .andExpect(jsonPath("$.createdBy").value("le.directeur"));

        // Premier niveau d'audit : colonnes techniques
        assertThat(jdbc.queryForObject("SELECT created_by FROM annee_scolaire", String.class))
                .isEqualTo("le.directeur");
        // Second niveau : révision Envers avec l'identité Keycloak
        assertThat(jdbc.queryForObject("""
                SELECT r.username || '/' || r.keycloak_user_id || '/' || a.revtype
                FROM annee_scolaire_aud a JOIN revinfo r ON r.rev_id = a.rev_id""", String.class))
                .isEqualTo("le.directeur/id-le.directeur/0");
    }

    @Test
    void lesSaisiesInvalidesOuEnDoubleSontRejetees() throws Exception {
        var directeur = utilisateur("le.directeur", "DIRECTEUR");
        mvc.perform(post("/api/annees-scolaires").with(directeur).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelle\": \"2026\", \"dateDebut\": \"2026-10-01\", \"dateFin\": \"2027-07-15\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/annees-scolaires").with(directeur).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelle\": \"2026-2027\", \"dateDebut\": \"2027-07-15\", \"dateFin\": \"2026-10-01\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/annees-scolaires").with(directeur)
                        .contentType(MediaType.APPLICATION_JSON).content(ANNEE_2026))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/annees-scolaires").with(directeur)
                        .contentType(MediaType.APPLICATION_JSON).content(ANNEE_2026))
                .andExpect(status().isConflict());
    }

    @Test
    void uneSeuleAnneeEstActiveALaFoisEtLHistoriqueLeRetrace() throws Exception {
        var directeur = utilisateur("le.directeur", "DIRECTEUR");
        var censeur = utilisateur("le.censeur", "CENSEUR");
        mvc.perform(post("/api/annees-scolaires").with(directeur)
                .contentType(MediaType.APPLICATION_JSON).content(ANNEE_2026)).andExpect(status().isCreated());
        mvc.perform(post("/api/annees-scolaires").with(directeur)
                .contentType(MediaType.APPLICATION_JSON).content(ANNEE_2027)).andExpect(status().isCreated());
        Long id2026 = jdbc.queryForObject("SELECT id FROM annee_scolaire WHERE libelle = '2026-2027'", Long.class);
        Long id2027 = jdbc.queryForObject("SELECT id FROM annee_scolaire WHERE libelle = '2027-2028'", Long.class);

        mvc.perform(get("/api/annees-scolaires/active").with(directeur)).andExpect(status().isNotFound());

        mvc.perform(post("/api/annees-scolaires/" + id2026 + "/activation").with(directeur))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(true));
        mvc.perform(post("/api/annees-scolaires/" + id2027 + "/activation").with(censeur))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(true));

        mvc.perform(get("/api/annees-scolaires/active").with(directeur))
                .andExpect(status().isOk()).andExpect(jsonPath("$.libelle").value("2027-2028"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM annee_scolaire WHERE actif", Integer.class))
                .isEqualTo(1);

        // 2026-2027 : créée, activée par le directeur, désactivée par le censeur
        mvc.perform(get("/api/annees-scolaires/" + id2026 + "/historique").with(directeur))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].action", contains("CREATION", "MODIFICATION", "MODIFICATION")))
                .andExpect(jsonPath("$[*].actif", contains(false, true, false)))
                .andExpect(jsonPath("$[*].auteur", contains("le.directeur", "le.directeur", "le.censeur")));

        // L'historique est réservé à la direction
        mvc.perform(get("/api/annees-scolaires/" + id2026 + "/historique")
                .with(utilisateur("la.caissiere", "ECONOME"))).andExpect(status().isForbidden());
    }
}
