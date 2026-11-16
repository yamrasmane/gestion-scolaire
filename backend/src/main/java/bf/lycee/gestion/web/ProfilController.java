package bf.lycee.gestion.web;

import bf.lycee.gestion.security.UtilisateurCourant;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Identité et rôles de l'utilisateur connecté, tels que vus par l'API. */
@RestController
@RequestMapping("/api/moi")
public class ProfilController {

    public record Profil(String identifiant, String nomUtilisateur, String nomComplet, String email,
                         List<String> roles) {
    }

    @GetMapping
    public Profil moi(JwtAuthenticationToken authentification) {
        var jeton = authentification.getToken();
        List<String> roles = authentification.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(autorite -> autorite.startsWith("ROLE_"))
                .map(autorite -> autorite.substring("ROLE_".length()))
                .sorted()
                .toList();
        return new Profil(jeton.getSubject(), UtilisateurCourant.nomUtilisateur().orElse(jeton.getSubject()),
                jeton.getClaimAsString("name"), jeton.getClaimAsString("email"), roles);
    }
}
