package bf.lycee.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bf.lycee.gestion.rapports.FormatRapport;
import bf.lycee.gestion.rapports.RapportException;
import bf.lycee.gestion.rapports.ServiceRapports;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Édition des documents : moteur générique et quatre états du lycée. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RapportsIntegrationTest {

    private static final RequestPostProcessor DIRECTEUR = jwt()
            .jwt(jeton -> jeton.subject("id-rapports").claim("preferred_username", "le.directeur"))
            .authorities(new SimpleGrantedAuthority("ROLE_DIRECTEUR"),
                    new SimpleGrantedAuthority("ROLE_INTENDANT"));

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ServiceRapports rapports;

    private UUID anneeCourante;
    private UUID paiement;
    private UUID inscription;
    private UUID premierTrimestre;
    private LocalDate journeePaiement;

    @BeforeAll
    void charger() throws Exception {
        jdbc.execute("TRUNCATE TABLE annee_scolaire, personnel, eleve, matiere, audit.revinfo "
                + "RESTART IDENTITY CASCADE");
        String script = new String(new ClassPathResource("db/donnees-demo.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        script = script.replaceAll("(?s)DO \\$\\$.*?\\$\\$;", "");
        jdbc.execute(script);

        anneeCourante = jdbc.queryForObject("SELECT id FROM annee_scolaire WHERE actif", UUID.class);
        premierTrimestre = jdbc.queryForObject(
                "SELECT id FROM trimestre WHERE annee_scolaire_id = ? AND numero = 1",
                UUID.class, anneeCourante);
        paiement = jdbc.queryForObject(
                "SELECT id FROM paiement WHERE statut = 'VALIDE' ORDER BY created_at LIMIT 1",
                UUID.class);
        journeePaiement = jdbc.queryForObject(
                "SELECT journee_caisse FROM paiement WHERE id = ?", LocalDate.class, paiement);
        inscription = jdbc.queryForObject(
                "SELECT i.id FROM inscription i JOIN bulletin b ON b.inscription_id = i.id "
                        + "WHERE b.trimestre_id = ? LIMIT 1", UUID.class, premierTrimestre);
    }

    // ---------- Moteur générique ----------

    @Test
    void le_moteur_produit_le_meme_etat_dans_chaque_format() {
        List<Map<String, String>> lignes = List.of(
                Map.of("numeroRecu", "REC-2026-000001", "montant", "15 000", "mode", "Espèces",
                        "reference", "", "payeur", "M. Ouédraogo", "caissier", "SAWADOGO Issa",
                        "statut", "Valide"));
        Map<String, Object> parametres = Map.of("JOURNEE", "12/10/2026", "TOTAL", "15 000",
                "TOTAUX_PAR_MODE", "Espèces : 15 000");

        // PDF : signature %PDF-
        byte[] pdf = rapports.produire("journal-caisse", lignes, parametres, FormatRapport.PDF)
                .contenu();
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");

        // XLSX et DOCX sont des archives ZIP : signature PK
        for (FormatRapport format : List.of(FormatRapport.XLSX, FormatRapport.DOCX)) {
            byte[] contenu = rapports.produire("journal-caisse", lignes, parametres, format)
                    .contenu();
            assertThat(contenu[0]).isEqualTo((byte) 'P');
            assertThat(contenu[1]).isEqualTo((byte) 'K');
        }

        // Les formats texte portent les données et l'entête passée en paramètre
        String csv = new String(rapports.produire("journal-caisse", lignes, parametres,
                FormatRapport.CSV).contenu(), StandardCharsets.UTF_8);
        assertThat(csv).contains("REC-2026-000001").contains("M. Ouédraogo");
        String html = new String(rapports.produire("journal-caisse", lignes, parametres,
                FormatRapport.HTML).contenu(), StandardCharsets.UTF_8);
        assertThat(html)
                .contains("JOURNAL DE CAISSE")   // entête : paramètre
                .contains("12/10/2026")          // entête : paramètre
                .contains("REC-2026-000001")     // ligne : données JSON
                .contains("SAWADOGO Issa");
    }

    @Test
    void le_modele_de_donnees_json_decrit_les_lignes() {
        String modele = rapports.modeleJson(List.of(Map.of("numeroRecu", "REC-2026-000001")));
        assertThat(modele).contains("\"lignes\"").contains("REC-2026-000001");
    }

    @Test
    void un_fichier_jasper_inconnu_est_signale_clairement() {
        assertThatThrownBy(() -> rapports.produire("inexistant", List.of(), Map.of(),
                FormatRapport.PDF))
                .isInstanceOf(RapportException.class)
                .hasMessageContaining("inexistant");
    }

    @Test
    void un_format_inconnu_est_refuse() {
        assertThatThrownBy(() -> FormatRapport.depuis("odt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pdf");
    }

    // ---------- Documents du lycée ----------

    @Test
    void le_recu_s_edite_en_pdf_et_son_modele_est_consultable() throws Exception {
        MvcResult pdf = mvc.perform(get("/api/rapports/recus/" + paiement).with(DIRECTEUR))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(pdf.getResponse().getContentType()).isEqualTo("application/pdf");
        assertThat(pdf.getResponse().getContentAsByteArray()).hasSizeGreaterThan(1000);
        assertThat(pdf.getResponse().getHeader("Content-Disposition")).contains("recu-REC-");

        String modele = mvc.perform(get("/api/rapports/recus/" + paiement)
                        .param("modele", "true").with(DIRECTEUR))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(modele).contains("\"lignes\"").contains("typeFrais");
    }

    @Test
    void le_bulletin_s_edite_et_le_format_est_au_choix() throws Exception {
        MvcResult pdf = mvc.perform(get("/api/rapports/bulletins/" + inscription
                        + "/trimestres/" + premierTrimestre).with(DIRECTEUR))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(pdf.getResponse().getContentAsByteArray()).hasSizeGreaterThan(1000);

        // Les matières du bulletin doivent figurer dans le document, pas seulement l'entête.
        String texte = mvc.perform(get("/api/rapports/bulletins/" + inscription
                        + "/trimestres/" + premierTrimestre)
                        .param("format", "csv").with(DIRECTEUR))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(texte).contains("Mathématiques").contains("BULLETIN DE NOTES");

        MvcResult classeur = mvc.perform(get("/api/rapports/bulletins/" + inscription
                        + "/trimestres/" + premierTrimestre)
                        .param("format", "xlsx").with(DIRECTEUR))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(classeur.getResponse().getContentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(classeur.getResponse().getHeader("Content-Disposition")).contains(".xlsx");
    }

    @Test
    void l_etat_des_impayes_et_le_journal_s_editent() throws Exception {
        mvc.perform(get("/api/rapports/impayes/" + anneeCourante).with(DIRECTEUR))
                .andExpect(status().isOk());

        MvcResult journal = mvc.perform(get("/api/rapports/journal-caisse")
                        .param("journee", journeePaiement.toString())
                        .param("format", "csv").with(DIRECTEUR))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(journal.getResponse().getContentAsString()).contains("REC-");
    }

    @Test
    void l_edition_est_reservee_aux_roles_financiers() throws Exception {
        RequestPostProcessor enseignant = jwt()
                .jwt(jeton -> jeton.claim("preferred_username", "prof.math"))
                .authorities(new SimpleGrantedAuthority("ROLE_ENSEIGNANT"));
        mvc.perform(get("/api/rapports/recus/" + paiement).with(enseignant))
                .andExpect(status().isForbidden());
    }
}
