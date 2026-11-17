package bf.lycee.gestion.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Accès à l'identité Keycloak de l'utilisateur connecté (claims du jeton JWT). */
public final class UtilisateurCourant {

    /** Auteur enregistré pour les traitements sans utilisateur (migrations, tâches planifiées). */
    public static final String SYSTEME = "system";

    private UtilisateurCourant() {
    }

    private static Optional<Jwt> jeton() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification instanceof JwtAuthenticationToken jwt && jwt.isAuthenticated()) {
            return Optional.of(jwt.getToken());
        }
        return Optional.empty();
    }

    /** Identifiant unique Keycloak (claim {@code sub}). */
    public static Optional<String> identifiant() {
        return jeton().map(Jwt::getSubject);
    }

    /** Adresse électronique du compte ({@code email}). */
    public static Optional<String> courriel() {
        return jeton().map(jwt -> jwt.getClaimAsString("email"))
                .filter(courriel -> !courriel.isBlank());
    }

    /** Nom d'utilisateur ({@code preferred_username}), à défaut le {@code sub}. */
    public static Optional<String> nomUtilisateur() {
        return jeton().map(jwt -> {
            String nom = jwt.getClaimAsString("preferred_username");
            return (nom != null && !nom.isBlank()) ? nom : jwt.getSubject();
        });
    }
}
