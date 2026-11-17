package bf.lycee.gestion.service;

import bf.lycee.gestion.domain.Personnel;
import bf.lycee.gestion.repository.PersonnelRepository;
import bf.lycee.gestion.security.UtilisateurCourant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fait le lien entre le compte Keycloak connecté et la fiche du personnel.
 *
 * <p>Quand les comptes ne sont pas créés par l'application (provisionnement
 * désactivé, comptes créés à la main dans Keycloak ou importés d'un annuaire),
 * la fiche ne porte pas encore d'identifiant Keycloak. Le rattachement se fait
 * alors à la première utilisation, par l'adresse électronique, et il est
 * enregistré pour les fois suivantes.</p>
 */
@Service
@Transactional
public class ComptePersonnelService {

    private final PersonnelRepository personnels;

    public ComptePersonnelService(PersonnelRepository personnels) {
        this.personnels = personnels;
    }

    public Optional<Personnel> connecte() {
        Optional<UUID> identifiant = UtilisateurCourant.identifiant().flatMap(ComptePersonnelService::versUuid);

        Optional<Personnel> parIdentifiant = identifiant.flatMap(personnels::findByKeycloakId);
        if (parIdentifiant.isPresent()) {
            return parIdentifiant;
        }

        Optional<Personnel> parCourriel = UtilisateurCourant.courriel()
                .flatMap(personnels::findByEmailIgnoreCase);
        parCourriel.filter(personnel -> personnel.getKeycloakId() == null)
                .ifPresent(personnel -> identifiant.ifPresent(personnel::setKeycloakId));
        return parCourriel;
    }

    private static Optional<UUID> versUuid(String valeur) {
        try {
            return Optional.of(UUID.fromString(valeur));
        } catch (IllegalArgumentException identifiantNonUuid) {
            return Optional.empty();
        }
    }
}
