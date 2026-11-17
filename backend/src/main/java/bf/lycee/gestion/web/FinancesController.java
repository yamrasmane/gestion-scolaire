package bf.lycee.gestion.web;

import bf.lycee.gestion.security.Roles;
import bf.lycee.gestion.service.CaisseService;
import bf.lycee.gestion.service.FacturationService;
import bf.lycee.gestion.service.PlanPaiementService;
import bf.lycee.gestion.service.TarifService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Frais, grille tarifaire, plans de paiement, encaissement (Modules 9 et 10). */
@RestController
@RequestMapping("/api")
public class FinancesController {

    private final TarifService tarifs;
    private final PlanPaiementService plans;
    private final FacturationService facturation;
    private final CaisseService caisse;

    public FinancesController(TarifService tarifs, PlanPaiementService plans,
                              FacturationService facturation, CaisseService caisse) {
        this.tarifs = tarifs;
        this.plans = plans;
        this.facturation = facturation;
        this.caisse = caisse;
    }

    // ---------- Types de frais (SF-21) ----------

    @GetMapping("/types-frais")
    public List<FinancesDto.TypeFraisVue> typesFrais(
            @RequestParam(defaultValue = "false") boolean actifsSeulement) {
        return tarifs.listerTypes(actifsSeulement);
    }

    @PostMapping("/types-frais")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.INTENDANT)
    public FinancesDto.TypeFraisVue creerTypeFrais(@Valid @RequestBody FinancesDto.TypeFraisSaisie saisie) {
        return tarifs.creerType(saisie);
    }

    @PutMapping("/types-frais/{id}")
    @PreAuthorize(Roles.INTENDANT)
    public FinancesDto.TypeFraisVue modifierTypeFrais(@PathVariable Long id,
                                                      @Valid @RequestBody FinancesDto.TypeFraisSaisie saisie) {
        return tarifs.modifierType(id, saisie);
    }

    @PostMapping("/types-frais/{id}/activation")
    @PreAuthorize(Roles.INTENDANT)
    public FinancesDto.TypeFraisVue activerTypeFrais(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "true") boolean actif) {
        return tarifs.activerType(id, actif);
    }

    // ---------- Grille tarifaire (SF-22) ----------

    @GetMapping("/annees-scolaires/{anneeId}/grille-tarifaire")
    public List<FinancesDto.TarifVue> grille(@PathVariable Long anneeId) {
        return tarifs.listerGrille(anneeId);
    }

    @PostMapping("/annees-scolaires/{anneeId}/grille-tarifaire")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.INTENDANT)
    public FinancesDto.TarifVue ajouterTarif(@PathVariable Long anneeId,
                                             @Valid @RequestBody FinancesDto.TarifSaisie saisie) {
        return tarifs.ajouterTarif(anneeId, saisie);
    }

    @PutMapping("/grille-tarifaire/{tarifId}")
    @PreAuthorize(Roles.INTENDANT)
    public FinancesDto.TarifVue modifierTarif(@PathVariable Long tarifId,
                                              @Valid @RequestBody FinancesDto.TarifSaisie saisie) {
        return tarifs.modifierTarif(tarifId, saisie);
    }

    @DeleteMapping("/grille-tarifaire/{tarifId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.INTENDANT)
    public void supprimerTarif(@PathVariable Long tarifId) {
        tarifs.supprimerTarif(tarifId);
    }

    @DeleteMapping("/annees-scolaires/{anneeId}/grille-tarifaire")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.INTENDANT)
    public void viderGrille(@PathVariable Long anneeId) {
        tarifs.viderGrille(anneeId);
    }

    @PostMapping("/annees-scolaires/{anneeId}/grille-tarifaire/duplication")
    @PreAuthorize(Roles.INTENDANT)
    public List<FinancesDto.TarifVue> dupliquerGrille(@PathVariable Long anneeId,
                                                      @Valid @RequestBody FinancesDto.DuplicationGrille saisie) {
        return tarifs.dupliquerGrille(anneeId, saisie);
    }

    // ---------- Plans de paiement (SF-26) ----------

    @GetMapping("/annees-scolaires/{anneeId}/plans-paiement")
    public List<FinancesDto.PlanVue> plans(@PathVariable Long anneeId) {
        return plans.lister(anneeId);
    }

    @PostMapping("/annees-scolaires/{anneeId}/plans-paiement")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.INTENDANT)
    public FinancesDto.PlanVue creerPlan(@PathVariable Long anneeId,
                                         @Valid @RequestBody FinancesDto.PlanSaisie saisie) {
        return plans.creer(anneeId, saisie);
    }

    @PutMapping("/plans-paiement/{planId}")
    @PreAuthorize(Roles.INTENDANT)
    public FinancesDto.PlanVue modifierPlan(@PathVariable Long planId,
                                            @Valid @RequestBody FinancesDto.PlanSaisie saisie) {
        return plans.modifier(planId, saisie);
    }

    @DeleteMapping("/plans-paiement/{planId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.INTENDANT)
    public void supprimerPlan(@PathVariable Long planId) {
        plans.supprimer(planId);
    }

    // ---------- Fiche financière de l'élève (SF-24, SF-25, SF-31) ----------

    @GetMapping("/inscriptions/{inscriptionId}/finances")
    @PreAuthorize(Roles.FINANCES_LECTURE)
    public FinancesDto.FicheFinanciere fiche(@PathVariable Long inscriptionId) {
        return facturation.situation(inscriptionId);
    }

    /** Génère ou recalcule les frais et l'échéancier de l'élève (SF-24). */
    @PostMapping("/inscriptions/{inscriptionId}/finances/generation")
    @PreAuthorize(Roles.INTENDANT)
    public List<String> genererFrais(@PathVariable Long inscriptionId) {
        return facturation.genererFrais(inscriptionId);
    }

    @PostMapping("/inscriptions/{inscriptionId}/finances/frais")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.INTENDANT)
    public FinancesDto.FraisVue ajouterFrais(@PathVariable Long inscriptionId,
                                             @Valid @RequestBody FinancesDto.FraisFacultatifSaisie saisie) {
        return facturation.ajouterFraisFacultatif(inscriptionId, saisie.typeFraisId());
    }

    @PutMapping("/frais-eleve/{fraisId}/remise")
    @PreAuthorize(Roles.INTENDANT + " or " + Roles.DIRECTION)
    public FinancesDto.FraisVue appliquerRemise(@PathVariable Long fraisId,
                                                @Valid @RequestBody FinancesDto.RemiseSaisie saisie,
                                                Authentication authentification) {
        boolean estDirecteur = authentification.getAuthorities().stream()
                .anyMatch(autorite -> "ROLE_DIRECTEUR".equals(autorite.getAuthority()));
        return facturation.appliquerRemise(fraisId, saisie, estDirecteur);
    }

    @DeleteMapping("/frais-eleve/{fraisId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.INTENDANT)
    public void supprimerFrais(@PathVariable Long fraisId) {
        facturation.supprimerFrais(fraisId);
    }

    // ---------- Caisse (SF-28 à SF-32) ----------

    @PostMapping("/inscriptions/{inscriptionId}/paiements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.CAISSE)
    public FinancesDto.PaiementVue encaisser(@PathVariable Long inscriptionId,
                                             @Valid @RequestBody FinancesDto.EncaissementSaisie saisie) {
        return caisse.encaisser(inscriptionId, saisie);
    }

    @PostMapping("/paiements/{paiementId}/annulation")
    @PreAuthorize(Roles.INTENDANT)
    public FinancesDto.PaiementVue annuler(@PathVariable Long paiementId,
                                           @Valid @RequestBody FinancesDto.AnnulationSaisie saisie) {
        return caisse.annuler(paiementId, saisie);
    }

    @GetMapping("/paiements/{paiementId}/recu")
    @PreAuthorize(Roles.FINANCES_LECTURE)
    public FinancesDto.Recu recu(@PathVariable Long paiementId,
                                 @RequestParam(defaultValue = "false") boolean duplicata) {
        return caisse.recu(paiementId, duplicata);
    }

    @GetMapping("/annees-scolaires/{anneeId}/impayes")
    @PreAuthorize(Roles.FINANCES_LECTURE)
    public List<FinancesDto.ImpayeVue> impayes(
            @PathVariable Long anneeId,
            @RequestParam(required = false) Long classeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate limite) {
        return caisse.impayes(anneeId, classeId, limite);
    }

    @GetMapping("/caisse/journal")
    @PreAuthorize(Roles.FINANCES_LECTURE)
    public FinancesDto.JournalCaisse journal(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journee) {
        return caisse.journal(journee != null ? journee : LocalDate.now());
    }

    @GetMapping("/annees-scolaires/{anneeId}/recouvrement")
    @PreAuthorize(Roles.FINANCES_LECTURE)
    public FinancesDto.Recouvrement recouvrement(@PathVariable Long anneeId) {
        return caisse.recouvrement(anneeId);
    }
}
