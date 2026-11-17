package bf.lycee.gestion.service;

import bf.lycee.gestion.domain.AnneeScolaire;
import bf.lycee.gestion.domain.ConfigCompositionCycle;
import bf.lycee.gestion.domain.Trimestre;
import bf.lycee.gestion.domain.enums.Cycle;
import bf.lycee.gestion.repository.AnneeScolaireRepository;
import bf.lycee.gestion.repository.ConfigCompositionCycleRepository;
import bf.lycee.gestion.repository.TrimestreRepository;
import bf.lycee.gestion.web.TrimestreDto.CompositionEffective;
import bf.lycee.gestion.web.TrimestreDto.Creation;
import bf.lycee.gestion.web.TrimestreDto.ReglageCycle;
import bf.lycee.gestion.web.TrimestreDto.ReglageTrimestre;
import bf.lycee.gestion.web.TrimestreDto.Vue;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Découpage de l'année en trimestres (SF-02) et option Composition (SF-03). */
@Service
@Transactional
public class TrimestreService {

    private final TrimestreRepository trimestres;
    private final ConfigCompositionCycleRepository configsCycle;
    private final AnneeScolaireRepository annees;

    public TrimestreService(TrimestreRepository trimestres, ConfigCompositionCycleRepository configsCycle,
                            AnneeScolaireRepository annees) {
        this.trimestres = trimestres;
        this.configsCycle = configsCycle;
        this.annees = annees;
    }

    @Transactional(readOnly = true)
    public List<Vue> lister(UUID anneeId) {
        List<ConfigCompositionCycle> configs = configsCycle.findByTrimestreAnneeScolaireId(anneeId);
        return trimestres.findByAnneeScolaireIdOrderByNumero(anneeId).stream()
                .map(t -> vue(t, configs))
                .toList();
    }

    public Vue creer(UUID anneeId, Creation saisie) {
        AnneeScolaire annee = annees.findById(anneeId)
                .orElseThrow(() -> new IntrouvableException("Année scolaire introuvable : " + anneeId));
        if (!saisie.dateFin().isAfter(saisie.dateDebut())) {
            throw new RegleMetierException("La date de fin doit être postérieure à la date de début");
        }
        if (saisie.dateDebut().isBefore(annee.getDateDebut()) || saisie.dateFin().isAfter(annee.getDateFin())) {
            throw new RegleMetierException("Le trimestre doit être compris dans l'année scolaire "
                    + annee.getLibelle());
        }
        for (Trimestre existant : trimestres.findByAnneeScolaireIdOrderByNumero(anneeId)) {
            if (existant.getNumero().equals(saisie.numero())) {
                throw new RegleMetierException("Le trimestre " + saisie.numero() + " existe déjà");
            }
            boolean chevauche = !saisie.dateDebut().isAfter(existant.getDateFin())
                    && !saisie.dateFin().isBefore(existant.getDateDebut());
            if (chevauche) {
                throw new RegleMetierException("Les dates chevauchent celles du trimestre "
                        + existant.getNumero());
            }
        }
        Trimestre trimestre = new Trimestre();
        trimestre.setAnneeScolaire(annee);
        trimestre.setNumero(saisie.numero());
        trimestre.setDateDebut(saisie.dateDebut());
        trimestre.setDateFin(saisie.dateFin());
        trimestre.setCloture(false);
        return vue(trimestres.save(trimestre), List.of());
    }

    /** Réglage de la composition au niveau du trimestre ; valeurs nulles = héritées de l'année. */
    public Vue reglerComposition(UUID trimestreId, ReglageTrimestre reglage) {
        Trimestre trimestre = modifiable(trimestreId);
        trimestre.setAvecComposition(reglage.avecComposition());
        trimestre.setPoidsComposition(reglage.poidsComposition());
        return vue(trimestres.save(trimestre));
    }

    /** Réglage propre à un cycle pour ce trimestre. */
    public Vue reglerCompositionCycle(UUID trimestreId, Cycle cycle, ReglageCycle reglage) {
        Trimestre trimestre = modifiable(trimestreId);
        ConfigCompositionCycle config = configsCycle.findByTrimestreIdAndCycle(trimestreId, cycle)
                .orElseGet(() -> {
                    ConfigCompositionCycle nouvelle = new ConfigCompositionCycle();
                    nouvelle.setTrimestre(trimestre);
                    nouvelle.setCycle(cycle);
                    return nouvelle;
                });
        config.setAvecComposition(reglage.avecComposition());
        config.setPoidsComposition(reglage.poidsComposition());
        configsCycle.saveAndFlush(config);
        return vue(trimestre);
    }

    /** Supprime le réglage propre au cycle : le cycle suit de nouveau le trimestre ou l'année. */
    public Vue supprimerReglageCycle(UUID trimestreId, Cycle cycle) {
        Trimestre trimestre = modifiable(trimestreId);
        configsCycle.findByTrimestreIdAndCycle(trimestreId, cycle).ifPresent(config -> {
            configsCycle.delete(config);
            configsCycle.flush();
        });
        return vue(trimestre);
    }

    /** Clôture : verrouille la saisie des notes et le réglage de la composition. */
    public Vue cloturer(UUID trimestreId) {
        Trimestre trimestre = trouver(trimestreId);
        trimestre.setCloture(true);
        return vue(trimestres.save(trimestre));
    }

    public Vue rouvrir(UUID trimestreId) {
        Trimestre trimestre = trouver(trimestreId);
        trimestre.setCloture(false);
        return vue(trimestres.save(trimestre));
    }

    /**
     * Réglage appliqué à un cycle pour un trimestre, par ordre de priorité :
     * trimestre + cycle, puis trimestre, puis année scolaire (SF-03).
     */
    public static CompositionEffective compositionEffective(Trimestre trimestre, Cycle cycle,
                                                            List<ConfigCompositionCycle> configs) {
        for (ConfigCompositionCycle config : configs) {
            if (config.getTrimestre().getId().equals(trimestre.getId()) && config.getCycle() == cycle) {
                return new CompositionEffective(config.isAvecComposition(), config.getPoidsComposition(), "CYCLE");
            }
        }
        AnneeScolaire annee = trimestre.getAnneeScolaire();
        if (trimestre.getAvecComposition() != null) {
            return new CompositionEffective(trimestre.getAvecComposition(),
                    trimestre.getPoidsComposition() != null
                            ? trimestre.getPoidsComposition() : annee.getPoidsCompositionDefaut(),
                    "TRIMESTRE");
        }
        return new CompositionEffective(annee.isAvecCompositionDefaut(), annee.getPoidsCompositionDefaut(), "ANNEE");
    }

    private Trimestre trouver(UUID id) {
        return trimestres.findById(id)
                .orElseThrow(() -> new IntrouvableException("Trimestre introuvable : " + id));
    }

    private Trimestre modifiable(UUID id) {
        Trimestre trimestre = trouver(id);
        if (trimestre.isCloture()) {
            throw new RegleMetierException("Le trimestre " + trimestre.getNumero()
                    + " est clôturé : son réglage n'est plus modifiable");
        }
        return trimestre;
    }

    private Vue vue(Trimestre trimestre) {
        return vue(trimestre,
                configsCycle.findByTrimestreAnneeScolaireId(trimestre.getAnneeScolaire().getId()));
    }

    private static Vue vue(Trimestre t, List<ConfigCompositionCycle> configs) {
        Map<Cycle, CompositionEffective> parCycle = new EnumMap<>(Cycle.class);
        for (Cycle cycle : Cycle.values()) {
            parCycle.put(cycle, compositionEffective(t, cycle, configs));
        }
        return new Vue(t.getId(), t.getAnneeScolaire().getId(), t.getNumero(), t.getDateDebut(), t.getDateFin(),
                t.getAvecComposition(), t.getPoidsComposition(), t.isCloture(), parCycle);
    }
}
