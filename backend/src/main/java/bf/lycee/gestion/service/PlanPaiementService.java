package bf.lycee.gestion.service;

import bf.lycee.gestion.domain.AnneeScolaire;
import bf.lycee.gestion.domain.Classe;
import bf.lycee.gestion.domain.Inscription;
import bf.lycee.gestion.domain.PlanPaiement;
import bf.lycee.gestion.domain.TranchePaiement;
import bf.lycee.gestion.domain.enums.ModeRepartition;
import bf.lycee.gestion.repository.AnneeScolaireRepository;
import bf.lycee.gestion.repository.ClasseRepository;
import bf.lycee.gestion.repository.NiveauRepository;
import bf.lycee.gestion.repository.PlanPaiementRepository;
import bf.lycee.gestion.repository.TranchePaiementRepository;
import bf.lycee.gestion.web.FinancesDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Plans de paiement par tranches configurables (SF-26). */
@Service
@Transactional
public class PlanPaiementService {

    private static final BigDecimal CENT = new BigDecimal("100");

    private final PlanPaiementRepository plans;
    private final TranchePaiementRepository tranches;
    private final AnneeScolaireRepository annees;
    private final NiveauRepository niveaux;
    private final ClasseRepository classes;

    public PlanPaiementService(PlanPaiementRepository plans, TranchePaiementRepository tranches,
                               AnneeScolaireRepository annees, NiveauRepository niveaux,
                               ClasseRepository classes) {
        this.plans = plans;
        this.tranches = tranches;
        this.annees = annees;
        this.niveaux = niveaux;
        this.classes = classes;
    }

    @Transactional(readOnly = true)
    /**
     * Reprend les plans d'une autre année, avec leurs tranches. Les plans
     * visant une classe précise sont laissés de côté — cette classe
     * appartient à l'année d'origine. Les dates d'échéance sont décalées du
     * nombre d'années qui sépare les deux rentrées, pour tomber au même
     * moment du calendrier scolaire.
     */
    public int copierPlans(UUID anneeSourceId, UUID anneeCibleId) {
        AnneeScolaire source = annee(anneeSourceId);
        AnneeScolaire cible = annee(anneeCibleId);
        List<PlanPaiement> aCopier = plans.findByAnneeScolaireIdOrderByLibelle(anneeSourceId).stream()
                .filter(plan -> plan.getClasse() == null)
                .toList();
        if (aCopier.isEmpty()) {
            return 0;
        }
        int decalage = cible.getDateDebut().getYear() - source.getDateDebut().getYear();
        for (PlanPaiement plan : plans.findByAnneeScolaireIdOrderByLibelle(anneeCibleId)) {
            tranches.deleteByPlanPaiementId(plan.getId());
            plans.delete(plan);
        }
        plans.flush();

        for (PlanPaiement origine : aCopier) {
            PlanPaiement copie = new PlanPaiement();
            copie.setAnneeScolaire(cible);
            copie.setLibelle(origine.getLibelle());
            copie.setNiveau(origine.getNiveau());
            copie.setCategorie(origine.getCategorie());
            copie.setModeRepartition(origine.getModeRepartition());
            copie.setParDefaut(origine.isParDefaut());
            copie.setMontantMinInscription(origine.getMontantMinInscription());
            plans.save(copie);
            for (TranchePaiement tranche : tranches
                    .findByPlanPaiementIdOrderByNumero(origine.getId())) {
                TranchePaiement nouvelle = new TranchePaiement();
                nouvelle.setPlanPaiement(copie);
                nouvelle.setNumero(tranche.getNumero());
                nouvelle.setLibelle(tranche.getLibelle());
                nouvelle.setDateEcheance(tranche.getDateEcheance() == null ? null
                        : tranche.getDateEcheance().plusYears(decalage));
                nouvelle.setPourcentage(tranche.getPourcentage());
                nouvelle.setMontant(tranche.getMontant());
                tranches.save(nouvelle);
            }
        }
        return aCopier.size();
    }

    private AnneeScolaire annee(UUID id) {
        return annees.findById(id)
                .orElseThrow(() -> new IntrouvableException("Année scolaire introuvable : " + id));
    }

    public List<FinancesDto.PlanVue> lister(UUID anneeId) {
        return plans.findByAnneeScolaireIdOrderByLibelle(anneeId).stream().map(this::versVue).toList();
    }

    public FinancesDto.PlanVue creer(UUID anneeId, FinancesDto.PlanSaisie saisie) {
        AnneeScolaire annee = annees.findById(anneeId)
                .orElseThrow(() -> new IntrouvableException("Année scolaire introuvable : " + anneeId));
        if (plans.existsByAnneeScolaireIdAndLibelleIgnoreCase(anneeId, saisie.libelle().trim())) {
            throw new RegleMetierException("Un plan porte déjà le libellé " + saisie.libelle());
        }
        PlanPaiement plan = new PlanPaiement();
        plan.setAnneeScolaire(annee);
        appliquer(plan, saisie);
        plans.save(plan);
        remplacerTranches(plan, saisie);
        return versVue(plan);
    }

    public FinancesDto.PlanVue modifier(UUID planId, FinancesDto.PlanSaisie saisie) {
        PlanPaiement plan = plan(planId);
        if (!plan.getLibelle().equalsIgnoreCase(saisie.libelle().trim())
                && plans.existsByAnneeScolaireIdAndLibelleIgnoreCase(
                        plan.getAnneeScolaire().getId(), saisie.libelle().trim())) {
            throw new RegleMetierException("Un plan porte déjà le libellé " + saisie.libelle());
        }
        appliquer(plan, saisie);
        remplacerTranches(plan, saisie);
        return versVue(plan);
    }

    public void supprimer(UUID planId) {
        PlanPaiement plan = plan(planId);
        tranches.deleteByPlanPaiementId(planId);
        plans.delete(plan);
    }

    /**
     * Plan applicable à une inscription (SF-26, même priorité que les tarifs) :
     * classe + catégorie, classe seule, niveau + catégorie, niveau seul, puis
     * le plan marqué « par défaut ».
     */
    @Transactional(readOnly = true)
    public Optional<PlanPaiement> resoudrePlan(Inscription inscription) {
        if (inscription.getPlanPaiement() != null) {
            return Optional.of(inscription.getPlanPaiement());
        }
        UUID anneeId = inscription.getAnneeScolaire().getId();
        Classe classe = inscription.getClasse();
        List<PlanPaiement> candidats = plans.findByAnneeScolaireIdOrderByLibelle(anneeId);

        return premier(candidats, p -> estClasse(p, classe) && p.getCategorie() == inscription.getCategorie())
                .or(() -> premier(candidats, p -> estClasse(p, classe) && p.getCategorie() == null))
                .or(() -> premier(candidats, p -> estNiveau(p, classe) && p.getCategorie() == inscription.getCategorie()))
                .or(() -> premier(candidats, p -> estNiveau(p, classe) && p.getCategorie() == null))
                .or(() -> premier(candidats, p -> p.getNiveau() == null && p.getClasse() == null
                        && p.getCategorie() == inscription.getCategorie()))
                .or(() -> plans.findByAnneeScolaireIdAndParDefautTrue(anneeId));
    }

    @Transactional(readOnly = true)
    public List<TranchePaiement> tranchesDuPlan(UUID planId) {
        return tranches.findByPlanPaiementIdOrderByNumero(planId);
    }

    // ---------- Outils ----------

    private void appliquer(PlanPaiement plan, FinancesDto.PlanSaisie saisie) {
        if (saisie.niveauId() != null && saisie.classeId() != null) {
            throw new SaisieInvalideException("Un plan vise un niveau ou une classe, pas les deux");
        }
        plan.setLibelle(saisie.libelle().trim());
        plan.setCategorie(saisie.categorie());
        plan.setModeRepartition(saisie.modeRepartition());
        plan.setMontantMinInscription(saisie.montantMinInscription() == null ? null
                : saisie.montantMinInscription().setScale(0, RoundingMode.HALF_UP));
        plan.setNiveau(saisie.niveauId() == null ? null : niveaux.findById(saisie.niveauId())
                .orElseThrow(() -> new IntrouvableException("Niveau introuvable : " + saisie.niveauId())));
        plan.setClasse(saisie.classeId() == null ? null : classes.findById(saisie.classeId())
                .orElseThrow(() -> new IntrouvableException("Classe introuvable : " + saisie.classeId())));

        if (saisie.parDefaut()) {
            // L'index unique partiel impose de retirer l'ancien plan par défaut d'abord.
            plans.findByAnneeScolaireIdAndParDefautTrue(plan.getAnneeScolaire().getId())
                    .filter(ancien -> !ancien.getId().equals(plan.getId()))
                    .ifPresent(ancien -> {
                        ancien.setParDefaut(false);
                        plans.saveAndFlush(ancien);
                    });
        }
        plan.setParDefaut(saisie.parDefaut());
    }

    /** Contrôles de cohérence du plan (SF-26) puis remplacement des tranches. */
    private void remplacerTranches(PlanPaiement plan, FinancesDto.PlanSaisie saisie) {
        List<FinancesDto.TrancheSaisie> saisies = new ArrayList<>(saisie.tranches());
        saisies.sort((a, b) -> a.numero().compareTo(b.numero()));

        BigDecimal somme = BigDecimal.ZERO;
        LocalDate precedente = null;
        for (int i = 0; i < saisies.size(); i++) {
            FinancesDto.TrancheSaisie t = saisies.get(i);
            if (!t.numero().equals(i + 1)) {
                throw new SaisieInvalideException("Les tranches doivent être numérotées de 1 à " + saisies.size());
            }
            if (precedente != null && !t.dateEcheance().isAfter(precedente)) {
                throw new RegleMetierException("Les dates d'échéance doivent être strictement croissantes");
            }
            precedente = t.dateEcheance();

            if (plan.getModeRepartition() == ModeRepartition.POURCENTAGE) {
                if (t.pourcentage() == null || t.pourcentage().signum() <= 0) {
                    throw new SaisieInvalideException("Tranche " + t.numero() + " : pourcentage attendu");
                }
                somme = somme.add(t.pourcentage());
            } else {
                if (t.montant() == null || t.montant().signum() < 0) {
                    throw new SaisieInvalideException("Tranche " + t.numero() + " : montant attendu");
                }
                somme = somme.add(t.montant());
            }
        }
        if (plan.getModeRepartition() == ModeRepartition.POURCENTAGE
                && somme.compareTo(CENT) != 0) {
            throw new RegleMetierException("La somme des pourcentages doit faire 100 % (actuellement " + somme + " %)");
        }
        if (plan.getModeRepartition() == ModeRepartition.MONTANT && somme.signum() <= 0) {
            throw new RegleMetierException("La somme des montants des tranches doit être positive");
        }

        tranches.deleteByPlanPaiementId(plan.getId());
        tranches.flush();
        for (FinancesDto.TrancheSaisie t : saisies) {
            TranchePaiement tranche = new TranchePaiement();
            tranche.setPlanPaiement(plan);
            tranche.setNumero(t.numero());
            tranche.setLibelle(t.libelle().trim());
            tranche.setDateEcheance(t.dateEcheance());
            if (plan.getModeRepartition() == ModeRepartition.POURCENTAGE) {
                tranche.setPourcentage(t.pourcentage().setScale(2, RoundingMode.HALF_UP));
                tranche.setMontant(null);
            } else {
                tranche.setMontant(t.montant().setScale(0, RoundingMode.HALF_UP));
                tranche.setPourcentage(null);
            }
            tranches.save(tranche);
        }
    }

    private static Optional<PlanPaiement> premier(List<PlanPaiement> candidats,
                                                  java.util.function.Predicate<PlanPaiement> filtre) {
        return candidats.stream().filter(filtre).findFirst();
    }

    private static boolean estClasse(PlanPaiement plan, Classe classe) {
        return plan.getClasse() != null && plan.getClasse().getId().equals(classe.getId());
    }

    private static boolean estNiveau(PlanPaiement plan, Classe classe) {
        return plan.getNiveau() != null && plan.getNiveau().getId().equals(classe.getNiveau().getId());
    }

    private PlanPaiement plan(UUID id) {
        return plans.findById(id)
                .orElseThrow(() -> new IntrouvableException("Plan de paiement introuvable : " + id));
    }

    private FinancesDto.PlanVue versVue(PlanPaiement plan) {
        List<FinancesDto.TrancheVue> vues = tranches.findByPlanPaiementIdOrderByNumero(plan.getId()).stream()
                .map(t -> new FinancesDto.TrancheVue(t.getId(), t.getNumero(), t.getLibelle(),
                        t.getDateEcheance(), t.getPourcentage(), t.getMontant()))
                .toList();
        return new FinancesDto.PlanVue(plan.getId(), plan.getLibelle(),
                plan.getNiveau() == null ? null : plan.getNiveau().getId(),
                plan.getNiveau() == null ? null : plan.getNiveau().getCode(),
                plan.getClasse() == null ? null : plan.getClasse().getId(),
                plan.getClasse() == null ? null : plan.getClasse().getNom(),
                plan.getCategorie(), plan.getModeRepartition(), plan.isParDefaut(),
                plan.getMontantMinInscription(), vues);
    }
}
