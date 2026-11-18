package bf.lycee.gestion.web;

import bf.lycee.gestion.domain.enums.CategorieEleve;
import bf.lycee.gestion.domain.enums.CategorieTarif;
import bf.lycee.gestion.domain.enums.ModePaiement;
import bf.lycee.gestion.domain.enums.ModeRepartition;
import bf.lycee.gestion.domain.enums.StatutEcheance;
import bf.lycee.gestion.domain.enums.StatutPaiement;
import bf.lycee.gestion.domain.enums.TypeRemise;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Échanges HTTP des modules financiers (SF-21 à SF-32). */
public final class FinancesDto {

    private FinancesDto() {
    }

    // ---------- Types de frais (SF-21) ----------

    public record TypeFraisVue(UUID id, String code, String libelle, boolean obligatoire,
                               boolean fractionnable, Integer prioriteImputation, boolean actif) {
    }

    public record TypeFraisSaisie(@NotBlank @Size(max = 30) String code,
                                  @NotBlank @Size(max = 100) String libelle,
                                  boolean obligatoire,
                                  boolean fractionnable,
                                  @NotNull @PositiveOrZero Integer prioriteImputation) {
    }

    // ---------- Grille tarifaire (SF-22) ----------

    public record TarifVue(UUID id, UUID typeFraisId, String typeFraisCode, String typeFraisLibelle,
                           UUID niveauId, String niveauCode, UUID classeId, String classeNom,
                           CategorieTarif categorie, BigDecimal montant) {
    }

    public record TarifSaisie(@NotNull UUID typeFraisId, UUID niveauId, UUID classeId,
                              @NotNull CategorieTarif categorie,
                              @NotNull @PositiveOrZero BigDecimal montant) {
    }

    /** Reconduction de la grille d'une autre année, avec hausse éventuelle (SF-22). */
    public record DuplicationGrille(@NotNull UUID anneeSourceId, BigDecimal haussePourcentage) {
    }

    // ---------- Plans de paiement (SF-26) ----------

    public record TrancheVue(UUID id, Integer numero, String libelle, LocalDate dateEcheance,
                             BigDecimal pourcentage, BigDecimal montant) {
    }

    public record PlanVue(UUID id, String libelle, UUID niveauId, String niveauCode,
                          UUID classeId, String classeNom, CategorieEleve categorie,
                          ModeRepartition modeRepartition, boolean parDefaut,
                          BigDecimal montantMinInscription, List<TrancheVue> tranches) {
    }

    public record TrancheSaisie(@NotNull Integer numero, @NotBlank @Size(max = 100) String libelle,
                                @NotNull LocalDate dateEcheance,
                                BigDecimal pourcentage, BigDecimal montant) {
    }

    public record PlanSaisie(@NotBlank @Size(max = 100) String libelle, UUID niveauId, UUID classeId,
                             CategorieEleve categorie, @NotNull ModeRepartition modeRepartition,
                             boolean parDefaut, BigDecimal montantMinInscription,
                             @NotEmpty @Valid List<TrancheSaisie> tranches) {
    }

    // ---------- Fiche financière de l'élève (SF-24, SF-25, SF-27, SF-31) ----------

    public record FraisVue(UUID id, UUID typeFraisId, String typeFraisCode, String typeFraisLibelle,
                           boolean obligatoire, boolean fractionnable, BigDecimal montantBrut,
                           TypeRemise remiseType, BigDecimal remiseValeur, BigDecimal montantRemise,
                           BigDecimal montantNet, String motifRemise, String remiseValideePar) {
    }

    public record EcheanceVue(UUID id, UUID fraisEleveId, String typeFraisLibelle, Integer trancheNumero,
                              String trancheLibelle, LocalDate dateEcheance, BigDecimal montantDu,
                              BigDecimal montantPaye, BigDecimal reste, StatutEcheance statut,
                              boolean derogatoire) {
    }

    public record ImputationVue(UUID echeanceId, String typeFraisLibelle, String trancheLibelle,
                                BigDecimal montant) {
    }

    public record PaiementVue(UUID id, String numeroRecu, LocalDate datePaiement, BigDecimal montant,
                              ModePaiement mode, String reference, String payeur, String caissier,
                              StatutPaiement statut, String motifAnnulation, String annulePar,
                              List<ImputationVue> imputations) {
    }

    public record FicheFinanciere(UUID inscriptionId, String matricule, String nom, String prenom,
                                  String classeNom, CategorieEleve categorie, String planPaiement,
                                  BigDecimal totalBrut, BigDecimal totalRemise, BigDecimal totalNet,
                                  BigDecimal totalPaye, BigDecimal resteAPayer,
                                  BigDecimal echuNonPaye, List<String> anomalies,
                                  List<FraisVue> frais, List<EcheanceVue> echeances,
                                  List<PaiementVue> paiements) {
    }

    public record FraisFacultatifSaisie(@NotNull UUID typeFraisId) {
    }

    public record RemiseSaisie(TypeRemise type, @PositiveOrZero BigDecimal valeur,
                               @Size(max = 255) String motif) {
    }

    // ---------- Encaissement (SF-28 à SF-30) ----------

    public record EncaissementSaisie(@NotNull LocalDate datePaiement,
                                     @NotNull @Positive BigDecimal montant,
                                     @NotNull ModePaiement mode,
                                     @Size(max = 100) String reference,
                                     @Size(max = 150) String payeur,
                                     /* Imputation manuelle sur un seul frais (SF-29) ; sinon automatique. */
                                     UUID typeFraisId) {
    }

    public record AnnulationSaisie(@NotBlank @Size(max = 255) String motif) {
    }

    /** Données du reçu, imprimables depuis le navigateur (SF-30). */
    public record Recu(String numeroRecu, boolean duplicata, LocalDate datePaiement,
                       String matricule, String nom, String prenom, String classeNom,
                       BigDecimal montant, String montantEnLettres, ModePaiement mode,
                       String reference, String payeur, String caissier, StatutPaiement statut,
                       BigDecimal resteAPayer, List<ImputationVue> imputations) {
    }

    // ---------- Impayés et journal de caisse (SF-31, SF-32) ----------

    public record ImpayeVue(UUID inscriptionId, String matricule, String nom, String prenom,
                            String classeNom, String typeFraisLibelle, String trancheLibelle,
                            LocalDate dateEcheance, BigDecimal montantDu, BigDecimal montantPaye,
                            BigDecimal reste, StatutEcheance statut) {
    }

    public record TotalParMode(ModePaiement mode, BigDecimal total) {
    }

    /** Le total et les totaux par mode portent sur la journée entière, pas sur la page. */
    public record JournalCaisse(LocalDate journee, BigDecimal total, List<TotalParMode> totauxParMode,
                                PageVue<PaiementVue> paiements) {
    }

    /** Tableau de bord du recouvrement d'une année (SF-32). */
    public record Recouvrement(BigDecimal attendu, BigDecimal encaisse, BigDecimal reste,
                               BigDecimal echuNonPaye, int elevesAJour, int elevesEnRetard) {
    }
}
