package bf.lycee.gestion.provisionnement;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Accès à l'API d'administration de Keycloak via un client confidentiel
 * (compte de service disposant des rôles manage-users / view-users).
 */
@ConfigurationProperties("app.keycloak.provisionnement")
public record ProprietesKeycloak(
        boolean active,
        String url,
        String realm,
        String clientId,
        String clientSecret,
        String motDePasseInitial) {
}
