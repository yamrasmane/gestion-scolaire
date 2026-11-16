package bf.lycee.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import bf.lycee.gestion.domain.Personnel;
import bf.lycee.gestion.domain.enums.RolePersonnel;
import bf.lycee.gestion.provisionnement.KeycloakProvisionnement;
import bf.lycee.gestion.provisionnement.ProprietesKeycloak;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Vérifie les appels envoyés à l'API d'administration de Keycloak (serveur simulé). */
class KeycloakProvisionnementTest {

    private static final String BASE = "http://keycloak.test";
    private static final String ID = "3f0c1f6e-8a3b-4c56-9d0e-1a2b3c4d5e6f";
    private static final String JETON = "{\"access_token\": \"jeton-admin\"}";
    private static final String ROLE = "{\"id\": \"r-1\", \"name\": \"ENSEIGNANT\"}";

    private MockRestServiceServer keycloak;
    private KeycloakProvisionnement provisionnement;
    private Personnel enseignant;

    @BeforeEach
    void preparer() {
        RestClient.Builder constructeur = RestClient.builder();
        keycloak = MockRestServiceServer.bindTo(constructeur).build();
        provisionnement = new KeycloakProvisionnement(constructeur,
                new ProprietesKeycloak(true, BASE, "lycee", "lycee-backend", "secret", "Initial123!"));
        enseignant = new Personnel();
        enseignant.setMatricule("P002");
        enseignant.setNom("SAWADOGO");
        enseignant.setPrenom("Issa");
        enseignant.setEmail("issa.sawadogo@lycee.bf");
        enseignant.setRole(RolePersonnel.ENSEIGNANT);
        enseignant.setActif(true);
    }

    private void attendreJeton() {
        keycloak.expect(requestTo(BASE + "/realms/lycee/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=client_credentials")))
                .andRespond(withSuccess(JETON, MediaType.APPLICATION_JSON));
    }

    private void attendreAttributionDuRole() {
        keycloak.expect(requestTo(BASE + "/admin/realms/lycee/roles/ENSEIGNANT"))
                .andRespond(withSuccess(ROLE, MediaType.APPLICATION_JSON));
        keycloak.expect(requestTo(BASE + "/admin/realms/lycee/users/" + ID + "/role-mappings/realm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$[0].name").value("ENSEIGNANT"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
    }

    @Test
    void creeLeCompteAvecMotDePasseTemporairePuisAttribueLeRole() {
        attendreJeton();
        HttpHeaders emplacement = new HttpHeaders();
        emplacement.setLocation(URI.create(BASE + "/admin/realms/lycee/users/" + ID));
        keycloak.expect(requestTo(BASE + "/admin/realms/lycee/users"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer jeton-admin"))
                .andExpect(jsonPath("$.username").value("issa.sawadogo@lycee.bf"))
                .andExpect(jsonPath("$.lastName").value("SAWADOGO"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.credentials[0].temporary").value(true))
                .andExpect(jsonPath("$.requiredActions[0]").value("UPDATE_PASSWORD"))
                .andRespond(withStatus(HttpStatus.CREATED).headers(emplacement));
        attendreAttributionDuRole();

        assertThat(provisionnement.creerCompte(enseignant)).isEqualTo(UUID.fromString(ID));
        keycloak.verify();
    }

    @Test
    void retrouveLeCompteLorsquIlExisteDeja() {
        attendreJeton();
        keycloak.expect(requestTo(BASE + "/admin/realms/lycee/users"))
                .andRespond(withStatus(HttpStatus.CONFLICT));
        keycloak.expect(requestTo(
                        BASE + "/admin/realms/lycee/users?username=issa.sawadogo@lycee.bf&exact=true"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\": \"" + ID + "\"}]", MediaType.APPLICATION_JSON));
        attendreAttributionDuRole();

        assertThat(provisionnement.creerCompte(enseignant)).isEqualTo(UUID.fromString(ID));
        keycloak.verify();
    }

    @Test
    void desactiveLeCompte() {
        attendreJeton();
        keycloak.expect(requestTo(BASE + "/admin/realms/lycee/users/" + ID))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.enabled").value(false))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        provisionnement.definirActivation(UUID.fromString(ID), false);
        keycloak.verify();
    }
}
