package bf.lycee.gestion.service;

import bf.lycee.gestion.domain.EcheanceEleve;
import bf.lycee.gestion.domain.FraisEleve;
import bf.lycee.gestion.domain.Inscription;
import bf.lycee.gestion.domain.PlanPaiement;
import bf.lycee.gestion.domain.TranchePaiement;
import bf.lycee.gestion.domain.TypeFrais;
import bf.lycee.gestion.domain.enums.ModeRepartition;
import bf.lycee.gestion.domain.enums.StatutEcheance;
import bf.lycee.gestion.domain.enums.StatutPaiement;
import bf.lycee.gestion.domain.enums.TypeRemise;
import bf.lycee.gestion.repository.EcheanceEleveRepository;
import bf.lycee.gestion.repository.FraisEleveRepository;
import bf.lycee.gestion.repository.ImputationPaiementRepository;
import bf.lycee.gestion.repository.InscriptionRepository;
import bf.lycee.gestion.repository.PaiementRepository;
import bf.lycee.gestion.repository.TypeFraisRepository;
import bf.lycee.gestion.security.UtilisateurCourant;
import bf.lycee.gestion.web.FinancesDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Frais dus par élève (SF-24), remises (SF-25) et échéancier individuel (SF-27).
 *
 * <p>Les montants sont en francs CFA, arrondis à l'unité ; l'écart d'arrondi de
 * la répartition est porté sur la dernière tranche.</p>
 */
@Service
@Transactional
public class FacturationService {

    private static final BigDecimal CENT = new BigDecimal("100");

    /** Ordre de service des échéances : la plus ancienne, puis la priorité du frais (SF-29). */
    private static final Comparator<EcheanceEleve> ORDRE_IMPUTATION =
            Comparator.comparing(EcheanceEleve::getDateEcheance)
                    .thenComparing((EcheanceEleve e) -> e.getFraisEleve().getTypeFrais().getPrioriteImputation())
                    .thenComparing(EcheanceEleve::getId);

    private static final Comparator<bf.lycee.gestion.domain.Paiement> ORDRE_PAIEMENTS =
            Comparator.comparing(bf.lycee.gestion.domain.Paiement::getDatePaiement)
                    .thenComparing(bf.lycee.gestion.domain.Paiement::getId);

    private final InscriptionRepository inscriptions;
    private final TypeFraisRepository typesFrais;
    private final FraisEleveRepository fraisEleves;
    private final EcheanceEleveRepository echeances;
    private final ImputationPaiementRepository imputations;
    private final PaiementRepository paiements;
    private final TarifService tarifs;
    private final PlanPaiementService plansPaiement;

    /** Au-delà de ce montant de remise, seul le Directeur peut valider (SF-25). */
    private final BigDecimal seuilRemise;

    public FacturationService(InscriptionRepository inscriptions, TypeFraisRepository typesFrais,
                              FraisEleveRepository fraisEleves, EcheanceEleveRepository echeances,
                              ImputationPaiementRepository imputations, PaiementRepository paiements,
                              TarifService tarifs, PlanPaiementService plansPaiement,
                              @Value("${app.finances.seuil-remise:25000}") BigDecimal seuilRemise) {
        this.inscriptions = inscriptions;
        this.typesFrais = typesFrais;
        this.fraisEleves = fraisEleves;
        this.echeances = echeances;
        this.imputations = imputations;
        this.paiements = paiements;
        this.tarifs = tarifs;
        this.plansPaiement = plansPaiement;
        this.seuilRemise = seuilRemise;
    }

    // ---------- Génération et recalcul ----------

    /**
     * Crée les frais obligatoires de l'inscription à partir de la grille, puis
     * l'échéancier (SF-24). Renvoie les anomalies rencontrées : un frais
     * obligatoire sans tarif n'interrompt pas le traitement, il est signalé.
     */
    public List<String> genererFrais(UUID inscriptionId) {
        Inscription inscription = inscription(inscriptionId);
        List<String> anomalies = new ArrayList<>();

        for (TypeFrais type : typesFrais.findByActifTrueOrderByPrioriteImputationAscLibelleAsc()) {
            if (!type.isObligatoire()) {
                continue;
            }
            Optional<BigDecimal> montant = tarifs.resoudreTarif(inscription.getAnneeScolaire().getId(),
                    inscription.getClasse(), inscription.getCategorie(), type.getId());
            if (montant.isEmpty()) {
                anomalies.add("Aucun tarif défini pour « " + type.getLibelle() + " » en "
                        + inscription.getClasse().getNom() + " (" + inscription.getCategorie() + ")");
                continue;
            }
            FraisEleve frais = fraisEleves
                    .findByInscriptionIdAndTypeFraisId(inscriptionId, type.getId())
                    .orElseGet(() -> nouveauFrais(inscription, type));
            frais.setMontantBrut(montant.get());
            recalculerRemise(frais);
            fraisEleves.save(frais);
        }

        anomalies.addAll(regenererEcheancier(inscription));
        return anomalies;
    }

    /** Ajoute un frais facultatif depuis la fiche financière (SF-24). */
    public FinancesDto.FraisVue ajouterFraisFacultatif(UUID inscriptionId, UUID typeFraisId) {
        Inscription inscription = inscription(inscriptionId);
        TypeFrais type = typesFrais.findById(typeFraisId)
                .orElseThrow(() -> new IntrouvableException("Type de frais introuvable : " + typeFraisId));
        if (fraisEleves.findByInscriptionIdAndTypeFraisId(inscriptionId, typeFraisId).isPresent()) {
            throw new RegleMetierException("Ce frais figure déjà sur la fiche de l'élève");
        }
        BigDecimal montant = tarifs.resoudreTarif(inscription.getAnneeScolaire().getId(),
                        inscription.getClasse(), inscription.getCategorie(), typeFraisId)
                .orElseThrow(() -> new RegleMetierException("Aucun tarif défini pour « "
                        + type.getLibelle() + " » en " + inscription.getClasse().getNom()));

        FraisEleve frais = nouveauFrais(inscription, type);
        frais.setMontantBrut(montant);
        recalculerRemise(frais);
        fraisEleves.save(frais);
        regenererEcheancier(inscription);
        return versVue(frais);
    }

    /** Remise ou exonération sur un frais, motif obligatoire (SF-25). */
    public FinancesDto.FraisVue appliquerRemise(UUID fraisId, FinancesDto.RemiseSaisie saisie,
                                                boolean estDirecteur) {
        FraisEleve frais = frais(fraisId);
        if (saisie.type() == null || saisie.valeur() == null || saisie.valeur().signum() == 0) {
            frais.setRemiseType(null);
            frais.setRemiseValeur(null);
            frais.setMotifRemise(null);
            frais.setRemiseValideePar(null);
        } else {
            if (saisie.motif() == null || saisie.motif().isBlank()) {
                throw new SaisieInvalideException("Le motif de la remise est obligatoire");
            }
            frais.setRemiseType(saisie.type());
            frais.setRemiseValeur(saisie.valeur());
            frais.setMotifRemise(saisie.motif().trim());
        }
        recalculerRemise(frais);

        if (frais.getMontantRemise().compareTo(seuilRemise) > 0) {
            if (!estDirecteur) {
                throw new RegleMetierException("Au-delà de " + seuilRemise.toPlainString()
                        + " FCFA, la remise doit être accordée par le Directeur");
            }
            frais.setRemiseValideePar(UtilisateurCourant.nomUtilisateur().orElse(UtilisateurCourant.SYSTEME));
        }

        regenererEcheancier(frais.getInscription());
        return versVue(frais);
    }

    /** Retire un frais facultatif, tant qu'aucun paiement ne lui est imputé. */
    public void supprimerFrais(UUID fraisId) {
        FraisEleve frais = frais(fraisId);
        if (frais.getTypeFrais().isObligatoire()) {
            throw new RegleMetierException("Un frais obligatoire ne peut pas être retiré de la fiche");
        }
        List<EcheanceEleve> liees = echeances.findByFraisEleveIdOrderByDateEcheanceAsc(fraisId);
        boolean dejaPaye = liees.stream().anyMatch(e -> e.getMontantPaye().signum() > 0);
        if (dejaPaye) {
            throw new RegleMetierException("Ce frais a déjà reçu un règlement : annulez le paiement d'abord");
        }
        Inscription inscription = frais.getInscription();
        echeances.deleteAll(liees);
        echeances.flush();
        fraisEleves.delete(frais);
        fraisEleves.flush();
        regenererEcheancier(inscription);
    }

    /**
     * Reconstruit l'échéancier de l'élève à partir du plan applicable, puis
     * réimpute les paiements valides du plus ancien au plus récent (SF-24,
     * SF-27). Les paiements eux-mêmes ne sont jamais modifiés.
     */
    public List<String> regenererEcheancier(Inscription inscription) {
        List<String> anomalies = new ArrayList<>();
        List<FraisEleve> frais = fraisEleves
                .findByInscriptionIdOrderByTypeFraisPrioriteImputation(inscription.getId());

        List<EcheanceEleve> anciennes = echeances
                .findByFraisEleveInscriptionIdOrderByDateEcheanceAsc(inscription.getId());
        for (EcheanceEleve ancienne : anciennes) {
            imputations.deleteAll(imputations.findByEcheanceEleveId(ancienne.getId()));
        }
        imputations.flush();
        echeances.deleteAll(anciennes);
        echeances.flush();

        Optional<PlanPaiement> plan = plansPaiement.resoudrePlan(inscription);
        List<TranchePaiement> tranches = plan
                .map(p -> plansPaiement.tranchesDuPlan(p.getId()))
                .orElse(List.of());
        if (tranches.isEmpty()) {
            anomalies.add("Aucun plan de paiement applicable : tout est exigible immédiatement");
        }

        for (FraisEleve f : frais) {
            for (EcheanceEleve echeance : repartir(f, plan.orElse(null), tranches, inscription)) {
                echeances.save(echeance);
            }
        }
        echeances.flush();

        reimputerPaiements(inscription);
        return anomalies;
    }

    /** Répartition d'un frais sur les tranches du plan (SF-26, SF-27). */
    private List<EcheanceEleve> repartir(FraisEleve frais, PlanPaiement plan,
                                         List<TranchePaiement> tranches, Inscription inscription) {
        BigDecimal net = frais.getMontantNet();
        List<EcheanceEleve> resultat = new ArrayList<>();

        if (tranches.isEmpty()) {
            resultat.add(nouvelleEcheance(frais, null, inscription.getDateInscription(), net));
            return resultat;
        }
        // Un frais non fractionnable est exigible en totalité à la première tranche.
        if (!frais.getTypeFrais().isFractionnable()) {
            TranchePaiement premiere = tranches.get(0);
            resultat.add(nouvelleEcheance(frais, premiere, premiere.getDateEcheance(), net));
            return resultat;
        }

        List<BigDecimal> parts = parts(plan, tranches);
        BigDecimal total = parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cumul = BigDecimal.ZERO;
        for (int i = 0; i < tranches.size(); i++) {
            BigDecimal montant;
            if (i == tranches.size() - 1) {
                // L'écart d'arrondi est porté sur la dernière tranche.
                montant = net.subtract(cumul);
            } else {
                montant = net.multiply(parts.get(i))
                        .divide(total, 0, RoundingMode.HALF_UP);
                cumul = cumul.add(montant);
            }
            if (montant.signum() < 0) {
                montant = BigDecimal.ZERO;
            }
            TranchePaiement tranche = tranches.get(i);
            resultat.add(nouvelleEcheance(frais, tranche, tranche.getDateEcheance(), montant));
        }
        return resultat;
    }

    /**
     * Poids de chaque tranche. En mode MONTANT, les montants du plan servent de
     * clé de répartition : le total dû de l'élève est partagé dans les mêmes
     * proportions, car il varie d'un élève à l'autre (remises, frais facultatifs).
     */
    private List<BigDecimal> parts(PlanPaiement plan, List<TranchePaiement> tranches) {
        boolean pourcentage = plan != null && plan.getModeRepartition() == ModeRepartition.POURCENTAGE;
        List<BigDecimal> parts = new ArrayList<>();
        for (TranchePaiement tranche : tranches) {
            BigDecimal part = pourcentage ? tranche.getPourcentage() : tranche.getMontant();
            parts.add(part == null ? BigDecimal.ZERO : part);
        }
        if (parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add).signum() == 0) {
            // Plan incohérent : répartition égale plutôt qu'une division par zéro.
            return tranches.stream().map(t -> BigDecimal.ONE).toList();
        }
        return parts;
    }

    /** Réimpute les paiements valides sur l'échéancier reconstruit. */
    private void reimputerPaiements(Inscription inscription) {
        for (EcheanceEleve echeance : echeances
                .findByFraisEleveInscriptionIdOrderByDateEcheanceAsc(inscription.getId())) {
            echeance.setMontantPaye(BigDecimal.ZERO);
        }
        echeances.flush();

        paiements.findByInscriptionIdOrderByDatePaiementDescIdDesc(inscription.getId()).stream()
                .filter(p -> p.getStatut() == StatutPaiement.VALIDE)
                .sorted(ORDRE_PAIEMENTS)
                .forEach(paiement -> imputer(paiement, paiement.getMontant(), null));
    }

    // ---------- Imputation (SF-29) ----------

    /**
     * Impute un montant sur les échéances non soldées de l'inscription : la plus
     * ancienne d'abord, puis, à date égale, selon l'ordre de priorité des types
     * de frais. Si {@code typeFraisId} est renseigné, seules les échéances de ce
     * frais sont servies (imputation manuelle).
     *
     * @return le montant effectivement imputé
     */
    public BigDecimal imputer(bf.lycee.gestion.domain.Paiement paiement, BigDecimal montant, UUID typeFraisId) {
        List<EcheanceEleve> candidates = echeances
                .findByFraisEleveInscriptionIdOrderByDateEcheanceAsc(paiement.getInscription().getId()).stream()
                .filter(e -> typeFraisId == null || e.getFraisEleve().getTypeFrais().getId().equals(typeFraisId))
                .filter(e -> reste(e).signum() > 0)
                .sorted(ORDRE_IMPUTATION)
                .toList();

        BigDecimal restant = montant;
        for (EcheanceEleve echeance : candidates) {
            if (restant.signum() <= 0) {
                break;
            }
            BigDecimal part = reste(echeance).min(restant);
            echeance.setMontantPaye(echeance.getMontantPaye().add(part));
            rafraichirStatut(echeance);

            bf.lycee.gestion.domain.ImputationPaiement imputation = new bf.lycee.gestion.domain.ImputationPaiement();
            imputation.setPaiement(paiement);
            imputation.setEcheanceEleve(echeance);
            imputation.setMontant(part);
            imputations.save(imputation);

            restant = restant.subtract(part);
        }
        return montant.subtract(restant);
    }

    // ---------- Consultation ----------

    @Transactional
    public FinancesDto.FicheFinanciere situation(UUID inscriptionId) {
        Inscription inscription = inscription(inscriptionId);
        List<FraisEleve> frais = fraisEleves
                .findByInscriptionIdOrderByTypeFraisPrioriteImputation(inscriptionId);
        List<EcheanceEleve> liste = echeances
                .findByFraisEleveInscriptionIdOrderByDateEcheanceAsc(inscriptionId);
        liste.forEach(this::rafraichirStatut);

        BigDecimal totalBrut = somme(frais, FraisEleve::getMontantBrut);
        BigDecimal totalRemise = somme(frais, FraisEleve::getMontantRemise);
        BigDecimal totalNet = somme(frais, FraisEleve::getMontantNet);
        BigDecimal totalPaye = liste.stream().map(EcheanceEleve::getMontantPaye)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal echuNonPaye = liste.stream()
                .filter(e -> !e.getDateEcheance().isAfter(LocalDate.now()))
                .map(FacturationService::reste)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<String> anomalies = new ArrayList<>();
        if (frais.isEmpty()) {
            anomalies.add("Aucun frais n'a encore été généré pour cet élève");
        }
        Optional<PlanPaiement> plan = plansPaiement.resoudrePlan(inscription);
        if (plan.isEmpty()) {
            anomalies.add("Aucun plan de paiement applicable à cet élève");
        }

        List<FinancesDto.PaiementVue> vuesPaiements = paiements
                .findByInscriptionIdOrderByDatePaiementDescIdDesc(inscriptionId).stream()
                .map(this::versVue)
                .toList();

        return new FinancesDto.FicheFinanciere(inscriptionId,
                inscription.getEleve().getMatricule(), inscription.getEleve().getNom(),
                inscription.getEleve().getPrenom(), inscription.getClasse().getNom(),
                inscription.getCategorie(), plan.map(PlanPaiement::getLibelle).orElse(null),
                totalBrut, totalRemise, totalNet, totalPaye, totalNet.subtract(totalPaye),
                echuNonPaye, anomalies,
                frais.stream().map(FacturationService::versVue).toList(),
                liste.stream().map(FacturationService::versVueEcheance).toList(),
                vuesPaiements);
    }

    @Transactional(readOnly = true)
    public List<EcheanceEleve> echeancesDe(UUID inscriptionId) {
        return echeances.findByFraisEleveInscriptionIdOrderByDateEcheanceAsc(inscriptionId);
    }

    /** Reste à payer de l'inscription, utilisé par la caisse (SF-28). */
    @Transactional(readOnly = true)
    public BigDecimal resteAPayer(UUID inscriptionId) {
        return echeances.findByFraisEleveInscriptionIdOrderByDateEcheanceAsc(inscriptionId).stream()
                .map(FacturationService::reste)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---------- Outils ----------

    public void rafraichirStatut(EcheanceEleve echeance) {
        BigDecimal reste = reste(echeance);
        StatutEcheance statut;
        if (reste.signum() <= 0) {
            statut = StatutEcheance.PAYEE;
        } else if (echeance.getDateEcheance().isBefore(LocalDate.now())) {
            statut = StatutEcheance.EN_RETARD;
        } else if (echeance.getMontantPaye().signum() > 0) {
            statut = StatutEcheance.PARTIEL;
        } else {
            statut = StatutEcheance.A_VENIR;
        }
        echeance.setStatut(statut);
    }

    public static BigDecimal reste(EcheanceEleve echeance) {
        return echeance.getMontantDu().subtract(echeance.getMontantPaye());
    }

    private void recalculerRemise(FraisEleve frais) {
        BigDecimal brut = frais.getMontantBrut();
        BigDecimal remise = BigDecimal.ZERO;
        if (frais.getRemiseType() == TypeRemise.POURCENTAGE && frais.getRemiseValeur() != null) {
            remise = brut.multiply(frais.getRemiseValeur()).divide(CENT, 0, RoundingMode.HALF_UP);
        } else if (frais.getRemiseType() == TypeRemise.MONTANT && frais.getRemiseValeur() != null) {
            remise = frais.getRemiseValeur().setScale(0, RoundingMode.HALF_UP);
        }
        if (remise.compareTo(brut) > 0) {
            remise = brut;
        }
        frais.setMontantRemise(remise);
        frais.setMontantNet(brut.subtract(remise));
    }

    private FraisEleve nouveauFrais(Inscription inscription, TypeFrais type) {
        FraisEleve frais = new FraisEleve();
        frais.setInscription(inscription);
        frais.setTypeFrais(type);
        frais.setMontantRemise(BigDecimal.ZERO);
        frais.setMontantNet(BigDecimal.ZERO);
        return frais;
    }

    private EcheanceEleve nouvelleEcheance(FraisEleve frais, TranchePaiement tranche,
                                           LocalDate date, BigDecimal montant) {
        EcheanceEleve echeance = new EcheanceEleve();
        echeance.setFraisEleve(frais);
        echeance.setTranchePaiement(tranche);
        echeance.setDateEcheance(date);
        echeance.setMontantDu(montant);
        echeance.setMontantPaye(BigDecimal.ZERO);
        echeance.setDerogatoire(false);
        rafraichirStatut(echeance);
        return echeance;
    }

    private static BigDecimal somme(List<FraisEleve> frais,
                                    java.util.function.Function<FraisEleve, BigDecimal> champ) {
        return frais.stream().map(champ).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Inscription inscription(UUID id) {
        return inscriptions.findById(id)
                .orElseThrow(() -> new IntrouvableException("Inscription introuvable : " + id));
    }

    private FraisEleve frais(UUID id) {
        return fraisEleves.findById(id)
                .orElseThrow(() -> new IntrouvableException("Frais introuvable : " + id));
    }

    static FinancesDto.FraisVue versVue(FraisEleve frais) {
        TypeFrais type = frais.getTypeFrais();
        return new FinancesDto.FraisVue(frais.getId(), type.getId(), type.getCode(), type.getLibelle(),
                type.isObligatoire(), type.isFractionnable(), frais.getMontantBrut(),
                frais.getRemiseType(), frais.getRemiseValeur(), frais.getMontantRemise(),
                frais.getMontantNet(), frais.getMotifRemise(), frais.getRemiseValideePar());
    }

    static FinancesDto.EcheanceVue versVueEcheance(EcheanceEleve echeance) {
        TranchePaiement tranche = echeance.getTranchePaiement();
        return new FinancesDto.EcheanceVue(echeance.getId(), echeance.getFraisEleve().getId(),
                echeance.getFraisEleve().getTypeFrais().getLibelle(),
                tranche == null ? null : tranche.getNumero(),
                tranche == null ? "Exigible immédiatement" : tranche.getLibelle(),
                echeance.getDateEcheance(), echeance.getMontantDu(), echeance.getMontantPaye(),
                reste(echeance), echeance.getStatut(), echeance.isDerogatoire());
    }

    FinancesDto.PaiementVue versVue(bf.lycee.gestion.domain.Paiement paiement) {
        List<FinancesDto.ImputationVue> lignes = imputations.findByPaiementId(paiement.getId()).stream()
                .map(i -> new FinancesDto.ImputationVue(i.getEcheanceEleve().getId(),
                        i.getEcheanceEleve().getFraisEleve().getTypeFrais().getLibelle(),
                        i.getEcheanceEleve().getTranchePaiement() == null ? "—"
                                : i.getEcheanceEleve().getTranchePaiement().getLibelle(),
                        i.getMontant()))
                .toList();
        return new FinancesDto.PaiementVue(paiement.getId(), paiement.getNumeroRecu(),
                paiement.getDatePaiement(), paiement.getMontant(), paiement.getMode(),
                paiement.getReference(), paiement.getPayeur(),
                paiement.getCaissier() == null ? paiement.getCreatedBy()
                        : paiement.getCaissier().getNom() + " " + paiement.getCaissier().getPrenom(),
                paiement.getStatut(), paiement.getMotifAnnulation(), paiement.getAnnulePar(), lignes);
    }
}
