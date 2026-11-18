package bf.lycee.gestion;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** Pagination, tri et recherche multicritère des listes. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaginationIntegrationTest {

    private static final RequestPostProcessor DIRECTEUR = utilisateur("le.directeur", "DIRECTEUR");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID classe;

    @BeforeEach
    void preparer() throws Exception {
        jdbc.execute("TRUNCATE TABLE annee_scolaire, personnel, eleve, matiere, audit.revinfo "
                + "RESTART IDENTITY CASCADE");
        mvc.perform(post("/api/annees-scolaires").with(DIRECTEUR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelle\": \"2026-2027\", \"dateDebut\": \"2026-10-01\","
                                + " \"dateFin\": \"2027-07-15\"}"))
                .andExpect(status().isCreated());
        UUID annee = jdbc.queryForObject("SELECT id FROM annee_scolaire WHERE libelle = '2026-2027'",
                UUID.class);
        UUID niveau = jdbc.queryForObject("SELECT id FROM niveau WHERE code = '6EME'", UUID.class);
        mvc.perform(post("/api/annees-scolaires/" + annee + "/classes").with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\": \"6ème A\", \"niveauId\": \"%s\", \"effectifMax\": 60}"
                                .formatted(niveau)))
                .andExpect(status().isCreated());
        classe = jdbc.queryForObject("SELECT id FROM classe WHERE nom = '6ème A'", UUID.class);
    }

    @Test
    void la_liste_du_personnel_est_paginee_triee_et_filtrable() throws Exception {
        creerPersonne("P-001", "ZOUNGRANA", "Ali", "ali.z@lycee.bf", "ENSEIGNANT");
        creerPersonne("P-002", "BAMBARA", "Awa", "awa.b@lycee.bf", "ENSEIGNANT");
        creerPersonne("P-003", "KABORE", "Salif", "salif.k@lycee.bf", "INTENDANT");

        // Page 0 de taille 2, triée par nom : BAMBARA puis KABORE.
        mvc.perform(get("/api/personnel").with(DIRECTEUR).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.pages").value(2))
                .andExpect(jsonPath("$.taille").value(2))
                .andExpect(jsonPath("$.contenu[0].nom").value("BAMBARA"))
                .andExpect(jsonPath("$.contenu[1].nom").value("KABORE"))
                .andExpect(jsonPath("$.contenu[2]").doesNotExist());

        // La page suivante ne rend que le reste.
        mvc.perform(get("/api/personnel").with(DIRECTEUR).param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.contenu[0].nom").value("ZOUNGRANA"))
                .andExpect(jsonPath("$.contenu[1]").doesNotExist());

        // Tri décroissant demandé par l'interface.
        mvc.perform(get("/api/personnel").with(DIRECTEUR).param("sort", "nom,desc"))
                .andExpect(jsonPath("$.contenu[0].nom").value("ZOUNGRANA"));

        // Recherche libre : le terme porte aussi sur l'adresse électronique.
        mvc.perform(get("/api/personnel").with(DIRECTEUR).param("recherche", "salif.k"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenu[0].matricule").value("P-003"));

        // Critères cumulés : rôle et état du compte.
        mvc.perform(get("/api/personnel").with(DIRECTEUR).param("role", "ENSEIGNANT"))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/personnel").with(DIRECTEUR)
                        .param("role", "ENSEIGNANT").param("actif", "false"))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void les_eleves_se_filtrent_par_sexe_et_date_de_naissance() throws Exception {
        inscrire("E-001", "OUEDRAOGO", "Awa", "2012-03-15", "F");
        inscrire("E-002", "SAWADOGO", "Issa", "2011-09-02", "M");
        inscrire("E-003", "TRAORE", "Moussa", "2013-01-20", "M");

        mvc.perform(get("/api/eleves").with(DIRECTEUR).param("sexe", "F"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenu[0].matricule").value("E-001"));

        // Intervalle de naissance, bornes incluses.
        mvc.perform(get("/api/eleves").with(DIRECTEUR)
                        .param("neApres", "2012-01-01").param("neAvant", "2012-12-31"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenu[0].matricule").value("E-001"));
        mvc.perform(get("/api/eleves").with(DIRECTEUR).param("neApres", "2012-01-01"))
                .andExpect(jsonPath("$.total").value(2));

        // Sexe et recherche libre se cumulent.
        mvc.perform(get("/api/eleves").with(DIRECTEUR).param("sexe", "M").param("recherche", "traore"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenu[0].matricule").value("E-003"));
    }

    @Test
    void les_inscriptions_d_une_classe_sont_paginees_et_filtrables() throws Exception {
        inscrire("E-010", "COMPAORE", "Fati", "2012-05-05", "F");
        inscrire("E-011", "DIALLO", "Ousmane", "2012-06-06", "M");

        mvc.perform(get("/api/classes/" + classe + "/inscriptions").with(DIRECTEUR)
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.pages").value(2))
                .andExpect(jsonPath("$.contenu[0].eleve.nom").value("COMPAORE"));

        mvc.perform(get("/api/classes/" + classe + "/inscriptions").with(DIRECTEUR)
                        .param("recherche", "diallo"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenu[0].eleve.matricule").value("E-011"));

        mvc.perform(get("/api/classes/" + classe + "/inscriptions").with(DIRECTEUR)
                        .param("statut", "ANNULEE"))
                .andExpect(jsonPath("$.total").value(0));
    }

    private void creerPersonne(String matricule, String nom, String prenom, String email, String role)
            throws Exception {
        mvc.perform(post("/api/personnel").with(DIRECTEUR).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"matricule": "%s", "nom": "%s", "prenom": "%s",
                                 "email": "%s", "telephone": "70000000", "role": "%s"}"""
                                .formatted(matricule, nom, prenom, email, role)))
                .andExpect(status().isCreated());
    }

    private void inscrire(String matricule, String nom, String prenom, String naissance, String sexe)
            throws Exception {
        mvc.perform(post("/api/classes/" + classe + "/inscriptions").with(DIRECTEUR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"matricule": "%s", "nom": "%s", "prenom": "%s",
                                 "dateNaissance": "%s", "sexe": "%s"}"""
                                .formatted(matricule, nom, prenom, naissance, sexe)))
                .andExpect(status().isCreated());
    }

    private static RequestPostProcessor utilisateur(String nom, String role) {
        return jwt()
                .jwt(jeton -> jeton.subject("id-" + nom).claim("preferred_username", nom))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
