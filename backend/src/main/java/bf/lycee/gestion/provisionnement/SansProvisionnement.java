package bf.lycee.gestion.provisionnement;

import bf.lycee.gestion.domain.Personnel;
import bf.lycee.gestion.domain.enums.RolePersonnel;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Utilisé quand le provisionnement Keycloak est désactivé : les comptes sont créés à la main. */
@Component
@ConditionalOnProperty(name = "app.keycloak.provisionnement.active", havingValue = "false", matchIfMissing = true)
public class SansProvisionnement implements ProvisionnementComptes {

    @Override
    public boolean actif() {
        return false;
    }

    @Override
    public UUID creerCompte(Personnel personnel) {
        return null;
    }

    @Override
    public void definirActivation(UUID keycloakId, boolean actif) {
        // rien à faire
    }

    @Override
    public void definirRole(UUID keycloakId, RolePersonnel role) {
        // rien à faire : le rôle est posé à la main dans Keycloak
    }
}
