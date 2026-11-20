package bf.lycee.gestion.service;

import bf.lycee.gestion.domain.AnneeScolaire;
import bf.lycee.gestion.repository.AnneeScolaireRepository;
import bf.lycee.gestion.web.AnneeScolaireDto;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Paramètres propres à une année scolaire : ceux qui changent d'une année à
 * l'autre et dont chaque année garde sa propre version.
 *
 * <p>Sont versionnés ainsi : le programme de chaque niveau (matières et
 * coefficients), le barème d'appréciations du bulletin, la grille tarifaire,
 * les plans de paiement, les trimestres et leurs réglages de composition, et
 * les options de l'année elle-même (composition par défaut, blocage des
 * bulletins impayés). Restent communs à toutes les années les catalogues qui
 * ne dépendent pas du calendrier : niveaux, matières et types de frais — leurs
 * montants, eux, vivent dans la grille tarifaire de chaque année.
 *
 * <p>Une année nouvellement créée hérite des paramètres de la précédente, ce
 * qui évite de tout ressaisir ; les modifications faites ensuite ne remontent
 * jamais vers les années antérieures.
 */
@Service
@Transactional
public class ParametresAnnuelsService {

    private static final Logger log = LoggerFactory.getLogger(ParametresAnnuelsService.class);

    private final AnneeScolaireRepository annees;
    private final AppreciationService appreciations;
    private final MatiereService matieres;
    private final TarifService tarifs;
    private final PlanPaiementService plans;

    public ParametresAnnuelsService(AnneeScolaireRepository annees, AppreciationService appreciations,
                                    MatiereService matieres, TarifService tarifs,
                                    PlanPaiementService plans) {
        this.annees = annees;
        this.appreciations = appreciations;
        this.matieres = matieres;
        this.tarifs = tarifs;
        this.plans = plans;
    }

    /**
     * Donne ses paramètres à une année qui vient d'être créée : ceux de
     * l'année précédente, ou les valeurs par défaut s'il n'y en a pas.
     */
    public AnneeScolaireDto.CopieParametres initialiser(AnneeScolaire annee) {
        Optional<AnneeScolaire> precedente = annees.findAllByOrderByDateDebutDesc().stream()
                .filter(autre -> !autre.getId().equals(annee.getId()))
                .filter(autre -> autre.getDateDebut().isBefore(annee.getDateDebut()))
                .findFirst();
        if (precedente.isEmpty()) {
            int poses = appreciations.installerDefauts(annee);
            log.info("Année {} : barème d'appréciations par défaut ({} intervalles)",
                    annee.getLibelle(), poses);
            return new AnneeScolaireDto.CopieParametres(null, 0, poses, 0, 0);
        }
        return copier(precedente.get().getId(), annee.getId());
    }

    /**
     * Recopie les paramètres d'une année vers une autre. Ce qui existait dans
     * l'année cible est remplacé : la copie est une reprise, pas un mélange.
     */
    public AnneeScolaireDto.CopieParametres copier(UUID anneeSourceId, UUID anneeCibleId) {
        AnneeScolaire source = annee(anneeSourceId);
        AnneeScolaire cible = annee(anneeCibleId);
        if (source.getId().equals(cible.getId())) {
            throw new RegleMetierException("Une année ne peut pas se copier sur elle-même");
        }

        int programmes = matieres.copierProgrammes(anneeSourceId, anneeCibleId);
        int appreciationsCopiees = appreciations.copier(anneeSourceId, anneeCibleId);
        if (appreciationsCopiees == 0) {
            appreciationsCopiees = appreciations.installerDefauts(cible);
        }
        int tarifsCopies = tarifs.copierGrille(anneeSourceId, anneeCibleId);
        int plansCopies = plans.copierPlans(anneeSourceId, anneeCibleId);

        // Les options de l'année se recopient aussi : elles font partie du
        // paramétrage annuel au même titre que le reste.
        cible.setAvecCompositionDefaut(source.isAvecCompositionDefaut());
        cible.setPoidsCompositionDefaut(source.getPoidsCompositionDefaut());
        cible.setBloquerBulletinImpaye(source.isBloquerBulletinImpaye());
        annees.save(cible);

        log.info("Paramètres repris de {} vers {} : {} lignes de programme, {} appréciations, "
                        + "{} tarifs, {} plans", source.getLibelle(), cible.getLibelle(),
                programmes, appreciationsCopiees, tarifsCopies, plansCopies);
        return new AnneeScolaireDto.CopieParametres(source.getLibelle(), programmes,
                appreciationsCopiees, tarifsCopies, plansCopies);
    }

    private AnneeScolaire annee(UUID id) {
        return annees.findById(id)
                .orElseThrow(() -> new IntrouvableException("Année scolaire introuvable : " + id));
    }
}
