package bf.lycee.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Charge le jeu de données de démonstration ({@code db/donnees-demo.sql}) et
 * interroge l'API dessus : si le schéma ou une règle de calcul change, ce test
 * signale que le jeu de données doit être régénéré.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DonneesDemoIntegrationTest {

    private static final RequestPostProcessor DIRECTEUR = jwt()
            .jwt(jeton -> jeton.subject("id-demo").claim("preferred_username", "le.directeur"))
            .authorities(new SimpleGrantedAuthority("ROLE_DIRECTEUR"),
                    new SimpleGrantedAuthority("ROLE_INTENDANT"));

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private Long anneeCourante;
    private Long premierTrimestre;
    private Long sixiemeA;

    @BeforeAll
    void charger() throws Exception {
        jdbc.execute("TRUNCATE TABLE annee_scolaire, personnel, eleve, matiere, revinfo "
                + "RESTART IDENTITY CASCADE");

        String script = new String(new ClassPathResource("db/donnees-demo.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        // Le garde-fou « base déjà peuplée » ne concerne que l'installation manuelle.
        script = script.replaceAll("(?s)DO \\$\\$.*?\\$\\$;", "");
        jdbc.execute(script);

        anneeCourante = jdbc.queryForObject(
                "SELECT id FROM annee_scolaire WHERE actif", Long.class);
        premierTrimestre = jdbc.queryForObject(
                "SELECT id FROM trimestre WHERE annee_scolaire_id = ? AND numero = 1",
                Long.class, anneeCourante);
        sixiemeA = jdbc.queryForObject(
                "SELECT id FROM classe WHERE annee_scolaire_id = ? AND nom = '6ème A'",
                Long.class, anneeCourante);

        // Le jeu de données doit être chargé en entier : sinon, les vérifications
        // qui suivent échoueraient sans dire ce qui manque.
        assertThat(compter("classe WHERE annee_scolaire_id = " + anneeCourante)).isEqualTo(8);
        assertThat(compter("inscription WHERE annee_scolaire_id = " + anneeCourante)).isEqualTo(144);
        assertThat(compter("note")).isGreaterThan(3000);
        assertThat(compter("echeance_eleve")).isGreaterThan(800);
        assertThat(compter("paiement WHERE statut = 'VALIDE'")).isGreaterThan(100);
        assertThat(compter("echeance_eleve WHERE statut = 'EN_RETARD'")).isGreaterThan(0);
        assertThat(compter("echeance_eleve WHERE date_echeance <= current_date"
                + " AND montant_du > montant_paye")).isGreaterThan(0);
    }

    private int compter(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    @Test
    void l_annee_en_cours_est_peuplee() throws Exception {
        mvc.perform(get("/api/annees-scolaires/active").with(DIRECTEUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelle").value("2026-2027"));

        mvc.perform(get("/api/annees-scolaires/" + anneeCourante + "/classes").with(DIRECTEUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8));

        mvc.perform(get("/api/classes/" + sixiemeA + "/inscriptions").with(DIRECTEUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(18));
    }

    @Test
    void le_classement_du_premier_trimestre_est_calculable() throws Exception {
        mvc.perform(get("/api/classes/" + sixiemeA + "/trimestres/" + premierTrimestre
                        + "/classement").with(DIRECTEUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(18))
                .andExpect(jsonPath("$[0].rang").value(1))
                .andExpect(jsonPath("$[0].moyenneGenerale").isNotEmpty());
    }

    @Test
    void le_bulletin_reprend_la_deliberation_enregistree() throws Exception {
        Long inscription = jdbc.queryForObject("""
                SELECT b.inscription_id FROM bulletin b
                JOIN inscription i ON i.id = b.inscription_id
                WHERE i.classe_id = ? AND b.mention IS NOT NULL
                ORDER BY b.rang LIMIT 1""", Long.class, sixiemeA);

        mvc.perform(get("/api/inscriptions/" + inscription + "/trimestres/" + premierTrimestre
                        + "/bulletin").with(DIRECTEUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes.length()").value(8))
                .andExpect(jsonPath("$.moyenneGenerale").isNotEmpty())
                .andExpect(jsonPath("$.mention").isNotEmpty())
                .andExpect(jsonPath("$.statistiques.effectif").value(18));
    }

    @Test
    void la_fiche_financiere_est_coherente_avec_les_reglements() throws Exception {
        Long inscription = jdbc.queryForObject("""
                SELECT f.inscription_id FROM frais_eleve f
                JOIN echeance_eleve e ON e.frais_eleve_id = f.id
                GROUP BY f.inscription_id
                HAVING sum(e.montant_paye) > 0 AND sum(e.montant_du - e.montant_paye) > 0
                LIMIT 1""", Long.class);

        String reponse = mvc.perform(get("/api/inscriptions/" + inscription + "/finances")
                        .with(DIRECTEUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frais.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.paiements.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andReturn().getResponse().getContentAsString();

        assertThat(reponse).contains("\"resteAPayer\"");

        BigDecimal attendu = jdbc.queryForObject("""
                SELECT sum(e.montant_du - e.montant_paye) FROM echeance_eleve e
                JOIN frais_eleve f ON f.id = e.frais_eleve_id
                WHERE f.inscription_id = ?""", BigDecimal.class, inscription);

        mvc.perform(get("/api/inscriptions/" + inscription + "/finances").with(DIRECTEUR))
                .andExpect(jsonPath("$.resteAPayer").value(attendu.doubleValue()));
    }

    @Test
    void les_impayes_et_le_journal_de_caisse_remontent_des_lignes() throws Exception {
        mvc.perform(get("/api/annees-scolaires/" + anneeCourante + "/impayes").with(DIRECTEUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)));

        mvc.perform(get("/api/caisse/journal").with(DIRECTEUR).param("journee", "2026-10-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThan(0.0)));

        mvc.perform(get("/api/annees-scolaires/" + anneeCourante + "/recouvrement").with(DIRECTEUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendu").value(org.hamcrest.Matchers.greaterThan(0.0)))
                .andExpect(jsonPath("$.elevesEnRetard").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void un_reglement_annule_conserve_son_recu_sans_imputation() throws Exception {
        Long paiement = jdbc.queryForObject(
                "SELECT id FROM paiement WHERE statut = 'ANNULE' LIMIT 1", Long.class);

        mvc.perform(get("/api/paiements/" + paiement + "/recu").with(DIRECTEUR)
                        .param("duplicata", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ANNULE"))
                .andExpect(jsonPath("$.imputations.length()").value(0))
                .andExpect(jsonPath("$.montantEnLettres").isNotEmpty());
    }
}
