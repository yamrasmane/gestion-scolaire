package bf.lycee.gestion.provisionnement;

import bf.lycee.gestion.domain.Personnel;
import java.util.UUID;

/** Création et (dés)activation des comptes de connexion du personnel. */
public interface ProvisionnementComptes {

    /** Indique si les comptes sont réellement créés dans Keycloak. */
    boolean actif();

    /**
     * Crée le compte (ou retrouve le compte existant) et lui attribue son rôle.
     *
     * @return identifiant Keycloak du compte
     */
    UUID creerCompte(Personnel personnel);

    void definirActivation(UUID keycloakId, boolean actif);
}
