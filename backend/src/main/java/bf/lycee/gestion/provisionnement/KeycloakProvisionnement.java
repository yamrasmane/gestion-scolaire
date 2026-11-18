package bf.lycee.gestion.provisionnement;

import bf.lycee.gestion.domain.Personnel;
import bf.lycee.gestion.domain.enums.RolePersonnel;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Provisionne les comptes du personnel dans Keycloak par son API Admin REST (SF-06) :
 * création du compte avec mot de passe temporaire à changer à la première
 * connexion, puis attribution du rôle du realm.
 */
@Component
@ConditionalOnProperty(name = "app.keycloak.provisionnement.active", havingValue = "true")
public class KeycloakProvisionnement implements ProvisionnementComptes {

    private static final ParameterizedTypeReference<Map<String, Object>> OBJET =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LISTE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient http;
    private final ProprietesKeycloak proprietes;

    public KeycloakProvisionnement(RestClient.Builder constructeur, ProprietesKeycloak proprietes) {
        this.http = constructeur.baseUrl(proprietes.url()).build();
        this.proprietes = proprietes;
    }

    @Override
    public boolean actif() {
        return true;
    }

    @Override
    public UUID creerCompte(Personnel personnel) {
        String jeton = jetonAdministration();
        String realm = proprietes.realm();
        String identifiant = personnel.getEmail().toLowerCase();

        Map<String, Object> compte = Map.of(
                "username", identifiant,
                "email", identifiant,
                "firstName", personnel.getPrenom(),
                "lastName", personnel.getNom(),
                "enabled", personnel.isActif(),
                "emailVerified", true,
                "requiredActions", List.of("UPDATE_PASSWORD"),
                "credentials", List.of(Map.of(
                        "type", "password",
                        "value", proprietes.motDePasseInitial(),
                        "temporary", true)));

        ResponseEntity<Void> reponse = http.post()
                .uri("/admin/realms/{realm}/users", realm)
                .headers(h -> h.setBearerAuth(jeton))
                .contentType(MediaType.APPLICATION_JSON)
                .body(compte)
                .retrieve()
                // 409 : le compte existe déjà, on le retrouve plus bas
                .onStatus(statut -> statut.value() == 409, (requete, rep) -> {
                })
                .toBodilessEntity();

        String id;
        URI emplacement = reponse.getHeaders().getLocation();
        if (reponse.getStatusCode().value() != 409 && emplacement != null) {
            String chemin = emplacement.getPath();
            id = chemin.substring(chemin.lastIndexOf('/') + 1);
        } else {
            List<Map<String, Object>> trouves = http.get()
                    .uri("/admin/realms/{realm}/users?username={u}&exact=true", realm, identifiant)
                    .headers(h -> h.setBearerAuth(jeton))
                    .retrieve()
                    .body(LISTE);
            if (trouves == null || trouves.isEmpty()) {
                throw new IllegalStateException("Compte Keycloak introuvable après création : " + identifiant);
            }
            id = String.valueOf(trouves.get(0).get("id"));
        }

        Map<String, Object> role = http.get()
                .uri("/admin/realms/{realm}/roles/{role}", realm, personnel.getRole().name())
                .headers(h -> h.setBearerAuth(jeton))
                .retrieve()
                .body(OBJET);
        http.post()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, id)
                .headers(h -> h.setBearerAuth(jeton))
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(role))
                .retrieve()
                .toBodilessEntity();
        return UUID.fromString(id);
    }

    /**
     * Retire les rôles métier du compte et pose celui de la fiche : sans ce
     * retrait, la personne cumulerait son ancien rôle et le nouveau.
     */
    @Override
    public void definirRole(UUID keycloakId, RolePersonnel role) {
        String jeton = jetonAdministration();
        String realm = proprietes.realm();
        List<String> metiers = Arrays.stream(RolePersonnel.values()).map(Enum::name).toList();

        List<Map<String, Object>> poses = http.get()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, keycloakId)
                .headers(h -> h.setBearerAuth(jeton))
                .retrieve()
                .body(LISTE);
        List<Map<String, Object>> aRetirer = poses == null ? List.of() : poses.stream()
                .filter(r -> metiers.contains(String.valueOf(r.get("name"))))
                .filter(r -> !role.name().equals(String.valueOf(r.get("name"))))
                .toList();
        if (!aRetirer.isEmpty()) {
            http.method(org.springframework.http.HttpMethod.DELETE)
                    .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, keycloakId)
                    .headers(h -> h.setBearerAuth(jeton))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(aRetirer)
                    .retrieve()
                    .toBodilessEntity();
        }

        Map<String, Object> nouveau = http.get()
                .uri("/admin/realms/{realm}/roles/{role}", realm, role.name())
                .headers(h -> h.setBearerAuth(jeton))
                .retrieve()
                .body(OBJET);
        http.post()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, keycloakId)
                .headers(h -> h.setBearerAuth(jeton))
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(nouveau))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void definirActivation(UUID keycloakId, boolean actif) {
        String jeton = jetonAdministration();
        http.put()
                .uri("/admin/realms/{realm}/users/{id}", proprietes.realm(), keycloakId)
                .headers(h -> h.setBearerAuth(jeton))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("enabled", actif))
                .retrieve()
                .toBodilessEntity();
    }

    private String jetonAdministration() {
        MultiValueMap<String, String> formulaire = new LinkedMultiValueMap<>();
        formulaire.add("grant_type", "client_credentials");
        formulaire.add("client_id", proprietes.clientId());
        formulaire.add("client_secret", proprietes.clientSecret());
        Map<String, Object> reponse = http.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", proprietes.realm())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formulaire)
                .retrieve()
                .body(OBJET);
        if (reponse == null || reponse.get("access_token") == null) {
            throw new IllegalStateException("Keycloak n'a pas délivré de jeton d'administration");
        }
        return String.valueOf(reponse.get("access_token"));
    }
}
