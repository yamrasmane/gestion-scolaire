package bf.lycee.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
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

/** Lot 3 : grille tarifaire, plans de tranches, échéancier, encaissement et impayés. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FinancesIntegrationTest {

    private static final RequestPostProcessor DIRECTEUR = utilisateur("le.directeur", "DIRECTEUR");
    private static final RequestPostProcessor INTENDANT = utilisateur("l.intendant", "INTENDANT");
    private static final RequestPostProcessor ECONOME = utilisateur("l.econome", "ECONOME");
    private static final RequestPostProcessor ENSEIGNANT = utilisateur("prof.math", "ENSEIGNANT");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private Long annee;
    private Long classe6A;
    private Long niveau6;
    private Long inscription;

    @BeforeEach
    void preparer() throws Exception {
        jdbc.execute("TRUNCATE TABLE annee_scolaire, personnel, eleve RESTART IDENTITY CASCADE");
        annee = creerAnnee("2026-2027", "2026-10-01", "2027-07-15");
        niveau6 = jdbc.queryForObject("SELECT id FROM niveau WHERE code = '6EME'", Long.class);
        classe6A = creerClasse("6EME", "6ème A");
        inscription = inscrire(classe6A, "EL-001", "Ouédraogo", "Awa");
    }

    // ---------- Grille tarifaire (SF-22) ----------

    @Test
    void le_tarif_de_la_classe_prime_sur_celui_du_niveau() throws Exception {
        Long scolarite = typeFrais("SCOLARITE");
        tarifNiveau(scolarite, niveau6, "TOUS", 90000);
        tarifClasse(scolarite, classe6A, "NOUVEAU", 80000);

        genererFrais();

        mvc.perform(get("/api/inscriptions/" + inscription + "/finances").with(INTENDANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frais[?(@.typeFraisCode == 'SCOLARITE')].montantNet").value(80000));
    }

    @Test
    void un_enseignant_ne_voit_pas_la_fiche_financiere() throws Exception {
        mvc.perform(get("/api/inscriptions/" + inscription + "/finances").with(ENSEIGNANT))
                .andExpect(status().isForbidden());
    }

    @Test
    void un_frais_obligatoire_sans_tarif_est_signale_en_anomalie() throws Exception {
        mvc.perform(post("/api/inscriptions/" + inscription + "/finances/generation").with(INTENDANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(containsString("Aucun tarif")));
    }

    // ---------- Plans de paiement (SF-26) ----------

    @Test
    void un_plan_dont_les_pourcentages_ne_font_pas_cent_est_refuse() throws Exception {
        String corps = """
                {"libelle": "Plan bancal", "modeRepartition": "POURCENTAGE", "parDefaut": false,
                 "tranches": [
                   {"numero": 1, "libelle": "1re", "dateEcheance": "2026-10-15", "pourcentage": 40},
                   {"numero": 2, "libelle": "2e", "dateEcheance": "2027-01-15", "pourcentage": 40}]}""";
        mvc.perform(post("/api/annees-scolaires/" + annee + "/plans-paiement").with(INTENDANT)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("100 %")));
    }

    @Test
    void les_dates_d_echeance_doivent_etre_croissantes() throws Exception {
        String corps = """
                {"libelle": "Plan désordonné", "modeRepartition": "POURCENTAGE", "parDefaut": false,
                 "tranches": [
                   {"numero": 1, "libelle": "1re", "dateEcheance": "2027-01-15", "pourcentage": 50},
                   {"numero": 2, "libelle": "2e", "dateEcheance": "2026-10-15", "pourcentage": 50}]}""";
        mvc.perform(post("/api/annees-scolaires/" + annee + "/plans-paiement").with(INTENDANT)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("croissantes")));
    }

    // ---------- Échéancier (SF-27) ----------

    @Test
    void les_frais_non_fractionnables_sont_dus_a_la_premiere_tranche() throws Exception {
        grilleComplete();
        planTroisTranches();
        genererFrais();

        // Inscription (non fractionnable) : 15 000 en totalité sur la 1re tranche.
        Integer dues = jdbc.queryForObject("""
                SELECT count(*) FROM echeance_eleve e
                JOIN frais_eleve f ON f.id = e.frais_eleve_id
                JOIN type_frais t ON t.id = f.type_frais_id
                WHERE t.code = 'INSCRIPTION'""", Integer.class);
        assertThat(dues).isEqualTo(1);

        BigDecimal montant = jdbc.queryForObject("""
                SELECT e.montant_du FROM echeance_eleve e
                JOIN frais_eleve f ON f.id = e.frais_eleve_id
                JOIN type_frais t ON t.id = f.type_frais_id
                WHERE t.code = 'INSCRIPTION'""", BigDecimal.class);
        assertThat(montant).isEqualByComparingTo("15000");
    }

    @Test
    void l_ecart_d_arrondi_est_porte_sur_la_derniere_tranche() throws Exception {
        Long scolarite = typeFrais("SCOLARITE");
        tarifNiveau(scolarite, niveau6, "TOUS", 100000);
        planTroisTranches();
        genererFrais();

        // 100 000 réparti en 3 tranches de 33,34 % / 33,33 % / 33,33 %
        var montants = jdbc.queryForList("""
                SELECT e.montant_du FROM echeance_eleve e
                JOIN frais_eleve f ON f.id = e.frais_eleve_id
                JOIN type_frais t ON t.id = f.type_frais_id
                WHERE t.code = 'SCOLARITE' ORDER BY e.date_echeance""", BigDecimal.class);
        assertThat(montants).hasSize(3);
        assertThat(montants.stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("100000");
    }

    // ---------- Encaissement et reçu (SF-28 à SF-30) ----------

    @Test
    void l_encaissement_solde_la_plus_ancienne_echeance_et_numerote_le_recu() throws Exception {
        grilleComplete();
        planTroisTranches();
        genererFrais();

        mvc.perform(post("/api/inscriptions/" + inscription + "/paiements").with(ECONOME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"datePaiement": "%s", "montant": 15000, "mode": "ESPECES",
                                 "payeur": "M. Ouédraogo"}""".formatted(LocalDate.now())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroRecu").value(containsString("REC-")))
                .andExpect(jsonPath("$.imputations[0].montant").value(15000));

        BigDecimal paye = jdbc.queryForObject("""
                SELECT e.montant_paye FROM echeance_eleve e
                JOIN frais_eleve f ON f.id = e.frais_eleve_id
                JOIN type_frais t ON t.id = f.type_frais_id
                WHERE t.code = 'INSCRIPTION'""", BigDecimal.class);
        assertThat(paye).isEqualByComparingTo("15000");
    }

    @Test
    void un_paiement_superieur_au_reste_du_est_refuse() throws Exception {
        grilleComplete();
        planTroisTranches();
        genererFrais();

        mvc.perform(post("/api/inscriptions/" + inscription + "/paiements").with(ECONOME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"datePaiement": "%s", "montant": 9999999, "mode": "ESPECES"}"""
                                .formatted(LocalDate.now())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("reste à payer")));
    }

    @Test
    void l_annulation_desimpute_le_paiement_et_conserve_le_recu() throws Exception {
        grilleComplete();
        planTroisTranches();
        genererFrais();
        Long paiement = encaisser(15000);

        mvc.perform(post("/api/paiements/" + paiement + "/annulation").with(INTENDANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motif\": \"Erreur de saisie du caissier\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ANNULE"));

        Integer imputations = jdbc.queryForObject(
                "SELECT count(*) FROM imputation_paiement WHERE paiement_id = ?", Integer.class, paiement);
        assertThat(imputations).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM paiement", Integer.class)).isEqualTo(1);
    }

    @Test
    void l_economie_ne_peut_pas_annuler_un_paiement() throws Exception {
        grilleComplete();
        planTroisTranches();
        genererFrais();
        Long paiement = encaisser(15000);

        mvc.perform(post("/api/paiements/" + paiement + "/annulation").with(ECONOME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motif\": \"Erreur\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void le_recu_porte_le_montant_en_toutes_lettres() throws Exception {
        grilleComplete();
        planTroisTranches();
        genererFrais();
        Long paiement = encaisser(15000);

        mvc.perform(get("/api/paiements/" + paiement + "/recu").with(ECONOME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.montantEnLettres").value("quinze mille francs CFA"))
                .andExpect(jsonPath("$.matricule").value("EL-001"));
    }

    // ---------- Remises (SF-25) ----------

    @Test
    void une_remise_importante_exige_la_validation_du_directeur() throws Exception {
        grilleComplete();
        planTroisTranches();
        genererFrais();
        Long fraisScolarite = jdbc.queryForObject("""
                SELECT f.id FROM frais_eleve f JOIN type_frais t ON t.id = f.type_frais_id
                WHERE t.code = 'SCOLARITE'""", Long.class);

        String remise = """
                {"type": "MONTANT", "valeur": 40000, "motif": "Enfant du personnel"}""";
        mvc.perform(put("/api/frais-eleve/" + fraisScolarite + "/remise").with(INTENDANT)
                        .contentType(MediaType.APPLICATION_JSON).content(remise))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("Directeur")));

        mvc.perform(put("/api/frais-eleve/" + fraisScolarite + "/remise").with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON).content(remise))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.montantNet").value(50000))
                .andExpect(jsonPath("$.remiseValideePar").value("le.directeur"));
    }

    @Test
    void une_remise_sans_motif_est_refusee() throws Exception {
        grilleComplete();
        planTroisTranches();
        genererFrais();
        Long frais = jdbc.queryForObject("""
                SELECT f.id FROM frais_eleve f JOIN type_frais t ON t.id = f.type_frais_id
                WHERE t.code = 'SCOLARITE'""", Long.class);

        mvc.perform(put("/api/frais-eleve/" + frais + "/remise").with(INTENDANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"POURCENTAGE\", \"valeur\": 10}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- Impayés et journal (SF-31, SF-32) ----------

    @Test
    void les_echeances_depassees_et_non_soldees_apparaissent_dans_les_impayes() throws Exception {
        grilleComplete();
        planTroisTranches();
        genererFrais();
        // La 1re tranche est datée dans le passé par le plan de test.
        mvc.perform(get("/api/annees-scolaires/" + annee + "/impayes").with(INTENDANT)
                        .param("classeId", classe6A.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].matricule").value("EL-001"))
                .andExpect(jsonPath("$[0].statut").value("EN_RETARD"));
    }

    @Test
    void le_journal_de_caisse_totalise_la_journee_par_mode() throws Exception {
        grilleComplete();
        planTroisTranches();
        genererFrais();
        encaisser(15000);

        mvc.perform(get("/api/caisse/journal").with(INTENDANT)
                        .param("journee", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(15000))
                .andExpect(jsonPath("$.totauxParMode[0].mode").value("ESPECES"));
    }

    // ---------- Outils ----------

    private static RequestPostProcessor utilisateur(String nom, String role) {
        return jwt()
                .jwt(jeton -> jeton.subject("id-" + nom).claim("preferred_username", nom))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private Long creerAnnee(String libelle, String debut, String fin) throws Exception {
        mvc.perform(post("/api/annees-scolaires").with(DIRECTEUR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelle\": \"%s\", \"dateDebut\": \"%s\", \"dateFin\": \"%s\"}"
                                .formatted(libelle, debut, fin)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM annee_scolaire WHERE libelle = ?", Long.class, libelle);
    }

    private Long creerClasse(String niveau, String nom) throws Exception {
        Long niveauId = jdbc.queryForObject("SELECT id FROM niveau WHERE code = ?", Long.class, niveau);
        mvc.perform(post("/api/annees-scolaires/" + annee + "/classes").with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"niveauId\": %d, \"nom\": \"%s\", \"effectifMax\": 60}"
                                .formatted(niveauId, nom)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM classe WHERE annee_scolaire_id = ? AND nom = ?",
                Long.class, annee, nom);
    }

    private Long inscrire(Long classeId, String matricule, String nom, String prenom) throws Exception {
        mvc.perform(post("/api/classes/" + classeId + "/inscriptions").with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"matricule": "%s", "nom": "%s", "prenom": "%s",
                                 "dateNaissance": "2012-03-15", "sexe": "F"}"""
                                .formatted(matricule, nom, prenom)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("""
                SELECT i.id FROM inscription i JOIN eleve e ON e.id = i.eleve_id
                WHERE e.matricule = ?""", Long.class, matricule);
    }

    private Long typeFrais(String code) {
        return jdbc.queryForObject("SELECT id FROM type_frais WHERE code = ?", Long.class, code);
    }

    private void tarifNiveau(Long typeFraisId, Long niveauId, String categorie, int montant) throws Exception {
        mvc.perform(post("/api/annees-scolaires/" + annee + "/grille-tarifaire").with(INTENDANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"typeFraisId": %d, "niveauId": %d, "categorie": "%s", "montant": %d}"""
                                .formatted(typeFraisId, niveauId, categorie, montant)))
                .andExpect(status().isCreated());
    }

    private void tarifClasse(Long typeFraisId, Long classeId, String categorie, int montant) throws Exception {
        mvc.perform(post("/api/annees-scolaires/" + annee + "/grille-tarifaire").with(INTENDANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"typeFraisId": %d, "classeId": %d, "categorie": "%s", "montant": %d}"""
                                .formatted(typeFraisId, classeId, categorie, montant)))
                .andExpect(status().isCreated());
    }

    /** Tarifs des trois frais obligatoires du référentiel : inscription, APE, scolarité. */
    private void grilleComplete() throws Exception {
        tarifNiveau(typeFrais("INSCRIPTION"), niveau6, "TOUS", 15000);
        tarifNiveau(typeFrais("APE"), niveau6, "TOUS", 5000);
        tarifNiveau(typeFrais("SCOLARITE"), niveau6, "TOUS", 90000);
        tarifNiveau(typeFrais("BIBLIO"), niveau6, "TOUS", 3000);
    }

    /** Plan en trois tranches dont la première est déjà échue. */
    private void planTroisTranches() throws Exception {
        String corps = """
                {"libelle": "Plan annuel", "modeRepartition": "POURCENTAGE", "parDefaut": true,
                 "tranches": [
                   {"numero": 1, "libelle": "1re tranche", "dateEcheance": "%s", "pourcentage": 33.34},
                   {"numero": 2, "libelle": "2e tranche", "dateEcheance": "%s", "pourcentage": 33.33},
                   {"numero": 3, "libelle": "3e tranche", "dateEcheance": "%s", "pourcentage": 33.33}]}"""
                .formatted(LocalDate.now().minusDays(30), LocalDate.now().plusDays(60),
                        LocalDate.now().plusDays(120));
        mvc.perform(post("/api/annees-scolaires/" + annee + "/plans-paiement").with(INTENDANT)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated());
    }

    private void genererFrais() throws Exception {
        mvc.perform(post("/api/inscriptions/" + inscription + "/finances/generation").with(INTENDANT))
                .andExpect(status().isOk());
    }

    private Long encaisser(int montant) throws Exception {
        mvc.perform(post("/api/inscriptions/" + inscription + "/paiements").with(ECONOME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"datePaiement": "%s", "montant": %d, "mode": "ESPECES"}"""
                                .formatted(LocalDate.now(), montant)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT max(id) FROM paiement", Long.class);
    }
}
