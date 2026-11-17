package bf.lycee.gestion.service;

import bf.lycee.gestion.domain.Classe;
import bf.lycee.gestion.domain.EcheanceEleve;
import bf.lycee.gestion.domain.ImputationPaiement;
import bf.lycee.gestion.domain.Inscription;
import bf.lycee.gestion.domain.Paiement;
import bf.lycee.gestion.domain.Personnel;
import bf.lycee.gestion.domain.enums.ModePaiement;
import bf.lycee.gestion.domain.enums.StatutEcheance;
import bf.lycee.gestion.domain.enums.StatutPaiement;
import bf.lycee.gestion.repository.ClasseRepository;
import bf.lycee.gestion.repository.EcheanceEleveRepository;
import bf.lycee.gestion.repository.ImputationPaiementRepository;
import bf.lycee.gestion.repository.InscriptionRepository;
import bf.lycee.gestion.repository.PaiementRepository;
import bf.lycee.gestion.security.UtilisateurCourant;
import bf.lycee.gestion.web.FinancesDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Encaissement, reçus et annulations (SF-28 à SF-30), suivi des impayés
 * (SF-31) et journal de caisse (SF-32).
 */
@Service
@Transactional
public class CaisseService {

    private final PaiementRepository paiements;
    private final ImputationPaiementRepository imputations;
    private final EcheanceEleveRepository echeances;
    private final InscriptionRepository inscriptions;
    private final ComptePersonnelService comptes;
    private final ClasseRepository classes;
    private final FacturationService facturation;

    public CaisseService(PaiementRepository paiements, ImputationPaiementRepository imputations,
                         EcheanceEleveRepository echeances, InscriptionRepository inscriptions,
                         ComptePersonnelService comptes, ClasseRepository classes,
                         FacturationService facturation) {
        this.paiements = paiements;
        this.imputations = imputations;
        this.echeances = echeances;
        this.inscriptions = inscriptions;
        this.comptes = comptes;
        this.classes = classes;
        this.facturation = facturation;
    }

    // ---------- Encaissement ----------

    /** Enregistre un règlement et l'impute sur les échéances (SF-28, SF-29). */
    public FinancesDto.PaiementVue encaisser(Long inscriptionId, FinancesDto.EncaissementSaisie saisie) {
        Inscription inscription = inscriptions.findById(inscriptionId)
                .orElseThrow(() -> new IntrouvableException("Inscription introuvable : " + inscriptionId));

        if (saisie.datePaiement().isAfter(LocalDate.now())) {
            throw new RegleMetierException("Un encaissement ne peut pas être enregistré à une date future");
        }
        BigDecimal montant = saisie.montant().setScale(0, RoundingMode.HALF_UP);
        BigDecimal reste = facturation.resteAPayer(inscriptionId);
        if (reste.signum() <= 0) {
            throw new RegleMetierException("La scolarité de cet élève est entièrement soldée");
        }
        if (montant.compareTo(reste) > 0) {
            throw new RegleMetierException("Le montant dépasse le reste à payer ("
                    + reste.toPlainString() + " FCFA)");
        }

        Paiement paiement = new Paiement();
        paiement.setNumeroRecu(prochainNumeroRecu(saisie.datePaiement()));
        paiement.setInscription(inscription);
        paiement.setDatePaiement(saisie.datePaiement());
        paiement.setMontant(montant);
        paiement.setMode(saisie.mode());
        paiement.setReference(saisie.reference());
        paiement.setPayeur(saisie.payeur());
        paiement.setStatut(StatutPaiement.VALIDE);
        paiement.setJourneeCaisse(saisie.datePaiement());
        caissierConnecte().ifPresent(paiement::setCaissier);
        paiements.save(paiement);

        BigDecimal impute = facturation.imputer(paiement, montant, saisie.typeFraisId());
        if (impute.compareTo(montant) < 0) {
            throw new RegleMetierException("Le montant dépasse ce qui reste dû sur le frais choisi ("
                    + impute.toPlainString() + " FCFA imputables)");
        }
        return facturation.versVue(paiement);
    }

    /**
     * Annule un paiement : le reçu est conservé, le montant est dé-imputé
     * (SF-30). Un paiement n'est jamais supprimé.
     */
    public FinancesDto.PaiementVue annuler(Long paiementId, FinancesDto.AnnulationSaisie saisie) {
        Paiement paiement = paiement(paiementId);
        if (paiement.getStatut() == StatutPaiement.ANNULE) {
            throw new RegleMetierException("Ce paiement est déjà annulé");
        }
        List<ImputationPaiement> lignes = imputations.findByPaiementId(paiementId);
        for (ImputationPaiement ligne : lignes) {
            EcheanceEleve echeance = ligne.getEcheanceEleve();
            echeance.setMontantPaye(echeance.getMontantPaye().subtract(ligne.getMontant()));
            facturation.rafraichirStatut(echeance);
        }
        imputations.deleteAll(lignes);

        paiement.setStatut(StatutPaiement.ANNULE);
        paiement.setMotifAnnulation(saisie.motif().trim());
        paiement.setAnnulePar(UtilisateurCourant.nomUtilisateur().orElse(UtilisateurCourant.SYSTEME));
        return facturation.versVue(paiement);
    }

    /** Données du reçu, imprimables depuis le navigateur (SF-30). */
    @Transactional(readOnly = true)
    public FinancesDto.Recu recu(Long paiementId, boolean duplicata) {
        Paiement paiement = paiement(paiementId);
        Inscription inscription = paiement.getInscription();
        FinancesDto.PaiementVue vue = facturation.versVue(paiement);
        return new FinancesDto.Recu(paiement.getNumeroRecu(), duplicata, paiement.getDatePaiement(),
                inscription.getEleve().getMatricule(), inscription.getEleve().getNom(),
                inscription.getEleve().getPrenom(), inscription.getClasse().getNom(),
                paiement.getMontant(), MontantEnLettres.francsCfa(paiement.getMontant()),
                paiement.getMode(), paiement.getReference(), paiement.getPayeur(),
                vue.caissier(), paiement.getStatut(),
                facturation.resteAPayer(inscription.getId()), vue.imputations());
    }

    // ---------- Impayés (SF-31) ----------

    /**
     * Échéances échues et non soldées d'une année, éventuellement limitées à une
     * classe. {@code limite} borne les échéances prises en compte (par défaut, ce jour).
     */
    @Transactional
    public List<FinancesDto.ImpayeVue> impayes(Long anneeId, Long classeId, LocalDate limite) {
        LocalDate borne = limite != null ? limite : LocalDate.now();
        List<Long> classeIds = classeId != null
                ? List.of(classeId)
                : classes.findByAnneeScolaireIdOrderByNom(anneeId).stream().map(Classe::getId).toList();
        if (classeIds.isEmpty()) {
            return List.of();
        }
        List<EcheanceEleve> liste = echeances.impayes(classeIds, StatutEcheance.PAYEE, borne);
        liste.forEach(facturation::rafraichirStatut);

        return liste.stream()
                .filter(e -> FacturationService.reste(e).signum() > 0)
                .map(e -> {
                    Inscription inscription = e.getFraisEleve().getInscription();
                    return new FinancesDto.ImpayeVue(inscription.getId(),
                            inscription.getEleve().getMatricule(), inscription.getEleve().getNom(),
                            inscription.getEleve().getPrenom(), inscription.getClasse().getNom(),
                            e.getFraisEleve().getTypeFrais().getLibelle(),
                            e.getTranchePaiement() == null ? "—" : e.getTranchePaiement().getLibelle(),
                            e.getDateEcheance(), e.getMontantDu(), e.getMontantPaye(),
                            FacturationService.reste(e), e.getStatut());
                })
                .toList();
    }

    // ---------- Journal de caisse et recouvrement (SF-32) ----------

    @Transactional(readOnly = true)
    public FinancesDto.JournalCaisse journal(LocalDate journee) {
        List<Paiement> liste = paiements.findByJourneeCaisseOrderByIdAsc(journee);
        Map<ModePaiement, BigDecimal> parMode = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Paiement paiement : liste) {
            if (paiement.getStatut() != StatutPaiement.VALIDE) {
                continue;
            }
            parMode.merge(paiement.getMode(), paiement.getMontant(), BigDecimal::add);
            total = total.add(paiement.getMontant());
        }
        List<FinancesDto.TotalParMode> totaux = parMode.entrySet().stream()
                .map(e -> new FinancesDto.TotalParMode(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(t -> t.mode().name()))
                .toList();
        return new FinancesDto.JournalCaisse(journee, total, totaux,
                liste.stream().map(facturation::versVue).toList());
    }

    /** Tableau de bord du recouvrement d'une année scolaire (SF-32). */
    @Transactional
    public FinancesDto.Recouvrement recouvrement(Long anneeId) {
        List<Long> classeIds = classes.findByAnneeScolaireIdOrderByNom(anneeId).stream()
                .map(Classe::getId).toList();
        BigDecimal attendu = BigDecimal.ZERO;
        BigDecimal encaisse = BigDecimal.ZERO;
        BigDecimal echuNonPaye = BigDecimal.ZERO;
        int aJour = 0;
        int enRetard = 0;

        List<Inscription> liste = new ArrayList<>();
        for (Long classeId : classeIds) {
            liste.addAll(inscriptions.findByClasseIdOrderByEleveNomAscElevePrenomAsc(classeId));
        }
        LocalDate aujourdHui = LocalDate.now();
        for (Inscription inscription : liste) {
            boolean retard = false;
            for (EcheanceEleve echeance : facturation.echeancesDe(inscription.getId())) {
                attendu = attendu.add(echeance.getMontantDu());
                encaisse = encaisse.add(echeance.getMontantPaye());
                BigDecimal reste = FacturationService.reste(echeance);
                if (!echeance.getDateEcheance().isAfter(aujourdHui) && reste.signum() > 0) {
                    echuNonPaye = echuNonPaye.add(reste);
                    retard = true;
                }
            }
            if (retard) {
                enRetard++;
            } else {
                aJour++;
            }
        }
        return new FinancesDto.Recouvrement(attendu, encaisse, attendu.subtract(encaisse),
                echuNonPaye, aJour, enRetard);
    }

    // ---------- Outils ----------

    /** Numérotation séquentielle par année civile : REC-2026-000154 (SF-30). */
    private String prochainNumeroRecu(LocalDate date) {
        String prefixe = "REC-" + date.getYear() + "-";
        String dernier = paiements.dernierNumeroRecu(prefixe);
        int suivant = 1;
        if (dernier != null && dernier.length() > prefixe.length()) {
            suivant = Integer.parseInt(dernier.substring(prefixe.length())) + 1;
        }
        return prefixe + String.format("%06d", suivant);
    }

    /** Le caissier est le membre du personnel dont le compte Keycloak est connecté. */
    private java.util.Optional<Personnel> caissierConnecte() {
        return comptes.connecte();
    }

    private Paiement paiement(Long id) {
        return paiements.findById(id)
                .orElseThrow(() -> new IntrouvableException("Paiement introuvable : " + id));
    }
}
