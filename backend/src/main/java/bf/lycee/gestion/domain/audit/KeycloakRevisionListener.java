package bf.lycee.gestion.domain.audit;

import bf.lycee.gestion.security.UtilisateurCourant;
import jakarta.servlet.http.HttpServletRequest;
import org.hibernate.envers.RevisionListener;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Renseigne l'auteur (Keycloak) et l'adresse IP sur chaque révision d'audit. */
public class KeycloakRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        Revision revision = (Revision) revisionEntity;
        revision.setKeycloakUserId(UtilisateurCourant.identifiant().orElse(null));
        revision.setUsername(UtilisateurCourant.nomUtilisateur().orElse(UtilisateurCourant.SYSTEME));
        revision.setIpAddress(adresseIp());
    }

    private static String adresseIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributs)) {
            return null;
        }
        HttpServletRequest requete = attributs.getRequest();
        String transmis = requete.getHeader("X-Forwarded-For");
        String ip = (transmis != null && !transmis.isBlank())
                ? transmis.split(",")[0].trim()
                : requete.getRemoteAddr();
        return (ip != null && ip.length() > 45) ? ip.substring(0, 45) : ip;
    }
}
