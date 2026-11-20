package bf.lycee.gestion.service;

import bf.lycee.gestion.domain.AnneeScolaire;
import bf.lycee.gestion.domain.BaremeAppreciation;
import bf.lycee.gestion.domain.enums.PorteeAppreciation;
import bf.lycee.gestion.repository.AnneeScolaireRepository;
import bf.lycee.gestion.repository.BaremeAppreciationRepository;
import bf.lycee.gestion.web.NotesDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appréciations du bulletin, réglées par intervalles de moyenne (SF-17).
 *
 * <p>Deux barèmes par année scolaire : l'un pour les moyennes de matière,
 * l'autre pour la moyenne générale. Les intervalles se lisent
 * {@code [borneMin, borneMax[}, 20 étant inclus dans le dernier. Une année
 * nouvellement créée reçoit un barème par défaut, qu'il suffit ensuite de
 * modifier ; la prise en compte au bulletin est automatique.
 */
@Service
@Transactional
public class AppreciationService {

    /** Barème livré par défaut, repris des usages courants des lycées. */
    private static final List<DefautBareme> DEFAUTS_MATIERE = List.of(
            new DefautBareme("0", "5", "Très insuffisant"),
            new DefautBareme("5", "8", "Insuffisant"),
            new DefautBareme("8", "10", "Médiocre"),
            new DefautBareme("10", "12", "Passable"),
            new DefautBareme("12", "14", "Assez bien"),
            new DefautBareme("14", "16", "Bien"),
            new DefautBareme("16", "18", "Très bien"),
            new DefautBareme("18", "20", "Excellent"));

    private static final List<DefautBareme> DEFAUTS_GENERALE = List.of(
            new DefautBareme("0", "8", "Avertissement"),
            new DefautBareme("12", "14", "Tableau d'honneur"),
            new DefautBareme("14", "16", "Encouragements"),
            new DefautBareme("16", "20", "Félicitations"));

    private record DefautBareme(String min, String max, String libelle) {
    }

    private final BaremeAppreciationRepository baremes;
    private final AnneeScolaireRepository annees;

    public AppreciationService(BaremeAppreciationRepository baremes, AnneeScolaireRepository annees) {
        this.baremes = baremes;
        this.annees = annees;
    }

    // ---------- Consultation ----------

    @Transactional(readOnly = true)
    public List<NotesDto.AppreciationVue> lister(UUID anneeId, PorteeAppreciation portee) {
        return baremes.findByAnneeScolaireIdAndPorteeOrderByBorneMin(anneeId, portee).stream()
                .map(AppreciationService::versVue)
                .toList();
    }

    /**
     * Barème prêt à l'emploi pour le calcul d'un bulletin : les intervalles
     * d'une année, rangés par portée. Lu une fois, puis appliqué à toutes les
     * lignes, pour ne pas interroger la base note par note.
     */
    @Transactional(readOnly = true)
    public Bareme baremeDe(UUID anneeId) {
        return new Bareme(
                baremes.findByAnneeScolaireIdAndPorteeOrderByBorneMin(anneeId,
                        PorteeAppreciation.MATIERE),
                baremes.findByAnneeScolaireIdAndPorteeOrderByBorneMin(anneeId,
                        PorteeAppreciation.GENERALE));
    }

    /** Barème d'une année, interrogeable moyenne par moyenne. */
    public record Bareme(List<BaremeAppreciation> matiere, List<BaremeAppreciation> generale) {

        public String appreciationMatiere(BigDecimal moyenne) {
            return libelle(matiere, moyenne);
        }

        public String mention(BigDecimal moyenneGenerale) {
            return libelle(generale, moyenneGenerale);
        }

        private static String libelle(List<BaremeAppreciation> intervalles, BigDecimal moyenne) {
            if (moyenne == null) {
                return null;
            }
            return intervalles.stream()
                    .filter(intervalle -> intervalle.contient(moyenne))
                    .map(BaremeAppreciation::getLibelle)
                    .findFirst()
                    .orElse(null);
        }
    }

    // ---------- Réglage ----------

    public NotesDto.AppreciationVue ajouter(UUID anneeId, PorteeAppreciation portee,
                                            NotesDto.AppreciationSaisie saisie) {
        AnneeScolaire annee = annee(anneeId);
        verifierIntervalle(saisie);
        verifierSansChevauchement(anneeId, portee, saisie, null);

        BaremeAppreciation intervalle = new BaremeAppreciation();
        intervalle.setAnneeScolaire(annee);
        intervalle.setPortee(portee);
        appliquer(intervalle, saisie);
        return versVue(baremes.save(intervalle));
    }

    public NotesDto.AppreciationVue modifier(UUID intervalleId, NotesDto.AppreciationSaisie saisie) {
        BaremeAppreciation intervalle = intervalle(intervalleId);
        verifierIntervalle(saisie);
        verifierSansChevauchement(intervalle.getAnneeScolaire().getId(), intervalle.getPortee(),
                saisie, intervalleId);
        appliquer(intervalle, saisie);
        return versVue(baremes.save(intervalle));
    }

    public void supprimer(UUID intervalleId) {
        baremes.delete(intervalle(intervalleId));
    }

    /**
     * Installe le barème par défaut pour une année qui n'en a pas encore.
     * Appelé à la création d'une année : les bulletins portent une
     * appréciation dès le premier trimestre, sans réglage préalable.
     */
    public int installerDefauts(AnneeScolaire annee) {
        if (baremes.existsByAnneeScolaireId(annee.getId())) {
            return 0;
        }
        int poses = enregistrer(annee, PorteeAppreciation.MATIERE, DEFAUTS_MATIERE);
        return poses + enregistrer(annee, PorteeAppreciation.GENERALE, DEFAUTS_GENERALE);
    }

    /** Reprend le barème d'une autre année : c'est la copie d'une version à l'autre. */
    public int copier(UUID anneeSourceId, UUID anneeCibleId) {
        AnneeScolaire cible = annee(anneeCibleId);
        List<BaremeAppreciation> source = baremes.findByAnneeScolaireId(anneeSourceId);
        if (source.isEmpty()) {
            return 0;
        }
        baremes.deleteAll(baremes.findByAnneeScolaireId(anneeCibleId));
        baremes.flush();
        for (BaremeAppreciation origine : source) {
            BaremeAppreciation copie = new BaremeAppreciation();
            copie.setAnneeScolaire(cible);
            copie.setPortee(origine.getPortee());
            copie.setBorneMin(origine.getBorneMin());
            copie.setBorneMax(origine.getBorneMax());
            copie.setLibelle(origine.getLibelle());
            baremes.save(copie);
        }
        return source.size();
    }

    // ---------- Contrôles ----------

    private static void verifierIntervalle(NotesDto.AppreciationSaisie saisie) {
        if (saisie.borneMax().compareTo(saisie.borneMin()) <= 0) {
            throw new RegleMetierException(
                    "La borne haute doit dépasser la borne basse : " + saisie.borneMin()
                            + " à " + saisie.borneMax());
        }
    }

    /**
     * Deux intervalles qui se chevauchent rendraient l'appréciation
     * imprévisible : on les refuse à la saisie plutôt que de trancher au
     * hasard au moment du bulletin.
     */
    private void verifierSansChevauchement(UUID anneeId, PorteeAppreciation portee,
                                           NotesDto.AppreciationSaisie saisie, UUID exclu) {
        for (BaremeAppreciation existant : baremes
                .findByAnneeScolaireIdAndPorteeOrderByBorneMin(anneeId, portee)) {
            if (existant.getId().equals(exclu)) {
                continue;
            }
            boolean chevauche = saisie.borneMin().compareTo(existant.getBorneMax()) < 0
                    && existant.getBorneMin().compareTo(saisie.borneMax()) < 0;
            if (chevauche) {
                throw new RegleMetierException("Cet intervalle en recouvre un autre : "
                        + existant.getBorneMin() + " à " + existant.getBorneMax()
                        + " (« " + existant.getLibelle() + " »)");
            }
        }
    }

    // ---------- Outils ----------

    private int enregistrer(AnneeScolaire annee, PorteeAppreciation portee,
                            List<DefautBareme> defauts) {
        for (DefautBareme defaut : defauts) {
            BaremeAppreciation intervalle = new BaremeAppreciation();
            intervalle.setAnneeScolaire(annee);
            intervalle.setPortee(portee);
            intervalle.setBorneMin(new BigDecimal(defaut.min()));
            intervalle.setBorneMax(new BigDecimal(defaut.max()));
            intervalle.setLibelle(defaut.libelle());
            baremes.save(intervalle);
        }
        return defauts.size();
    }

    private static void appliquer(BaremeAppreciation intervalle, NotesDto.AppreciationSaisie saisie) {
        intervalle.setBorneMin(saisie.borneMin());
        intervalle.setBorneMax(saisie.borneMax());
        intervalle.setLibelle(saisie.libelle().trim());
    }

    private static NotesDto.AppreciationVue versVue(BaremeAppreciation intervalle) {
        return new NotesDto.AppreciationVue(intervalle.getId(),
                intervalle.getAnneeScolaire().getId(), intervalle.getPortee(),
                intervalle.getBorneMin(), intervalle.getBorneMax(), intervalle.getLibelle());
    }

    private AnneeScolaire annee(UUID anneeId) {
        return annees.findById(anneeId).orElseThrow(
                () -> new IntrouvableException("Année scolaire introuvable : " + anneeId));
    }

    private BaremeAppreciation intervalle(UUID intervalleId) {
        return baremes.findById(intervalleId).orElseThrow(
                () -> new IntrouvableException("Intervalle introuvable : " + intervalleId));
    }

    /** Vrai si l'année possède au moins un intervalle : utile aux écrans. */
    @Transactional(readOnly = true)
    public Map<PorteeAppreciation, Integer> compter(UUID anneeId) {
        return Map.of(
                PorteeAppreciation.MATIERE,
                baremes.findByAnneeScolaireIdAndPorteeOrderByBorneMin(anneeId,
                        PorteeAppreciation.MATIERE).size(),
                PorteeAppreciation.GENERALE,
                baremes.findByAnneeScolaireIdAndPorteeOrderByBorneMin(anneeId,
                        PorteeAppreciation.GENERALE).size());
    }
}
