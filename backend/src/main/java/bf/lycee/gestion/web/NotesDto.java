package bf.lycee.gestion.web;

import bf.lycee.gestion.domain.enums.CategorieEvaluation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Échanges HTTP des modules notes, moyennes, classement et bulletins (SF-09 à SF-18). */
public final class NotesDto {

    private NotesDto() {
    }

    // ---------- Matières et enseignements (SF-09, SF-10) ----------

    public record MatiereVue(UUID id, String code, String libelle) {
    }

    public record MatiereSaisie(@NotBlank @Size(max = 20) String code,
                                @NotBlank @Size(max = 100) String libelle) {
    }

    public record EnseignementVue(UUID id, UUID classeId, String classeNom, UUID matiereId,
                                  String matiereCode, String matiereLibelle, UUID enseignantId,
                                  String enseignantNom, BigDecimal coefficient) {
    }

    public record EnseignementSaisie(@NotNull UUID matiereId, UUID enseignantId,
                                     @NotNull @Positive BigDecimal coefficient) {
    }

    /** Affectation d'un professeur à une matière d'une classe (SF-10). */
    public record AffectationSaisie(@NotNull UUID enseignementId, UUID enseignantId) {
    }

    public record AffectationsSaisie(@NotEmpty @Valid List<AffectationSaisie> affectations) {
    }

    // ---------- Programme d'un niveau (SF-09) ----------

    public record LigneProgrammeVue(UUID id, UUID niveauId, String niveauCode, String niveauLibelle,
                                    UUID matiereId, String matiereCode, String matiereLibelle,
                                    BigDecimal coefficient) {
    }

    public record LigneProgrammeSaisie(@NotNull UUID matiereId,
                                       @NotNull @Positive BigDecimal coefficient) {
    }

    /** Compte rendu de l'application d'un programme aux classes d'un niveau. */
    public record ApplicationProgramme(int classesTouchees, int matieresAjoutees,
                                       int coefficientsMisAJour, List<String> details) {
    }

    /** Service d'un enseignant : ce qu'il enseigne, et où. */
    public record ChargeEnseignant(UUID enseignantId, String enseignantNom, int nombreClasses,
                                   BigDecimal totalCoefficients, List<EnseignementVue> enseignements) {
    }

    // ---------- Évaluations et saisie des notes (SF-11, SF-12) ----------

    public record EvaluationVue(UUID id, UUID enseignementId, UUID trimestreId,
                                CategorieEvaluation categorie, String libelle, LocalDate dateEval,
                                BigDecimal bareme) {
    }

    public record EvaluationSaisie(@NotNull CategorieEvaluation categorie,
                                   @NotBlank @Size(max = 50) String libelle,
                                   LocalDate dateEval,
                                   @DecimalMin("1") @DecimalMax("100") BigDecimal bareme) {
    }

    /** Une case de la grille de saisie. */
    public record NoteSaisie(@NotNull UUID inscriptionId, UUID evaluationId,
                             @DecimalMin("0") @DecimalMax("20") BigDecimal valeur,
                             boolean absent,
                             @Size(max = 255) String appreciation) {
    }

    public record GrilleSaisie(@Valid List<NoteSaisie> notes) {
    }

    public record NoteVue(UUID inscriptionId, UUID evaluationId, BigDecimal valeur, boolean absent,
                          String appreciation) {
    }

    /** Grille de saisie d'un enseignement pour un trimestre (SF-12). */
    public record GrilleVue(UUID enseignementId, String matiereLibelle, String classeNom,
                            UUID trimestreId, boolean avecComposition, BigDecimal poidsComposition,
                            boolean trimestreCloture, List<EvaluationVue> evaluations,
                            List<LigneGrille> lignes) {
    }

    public record LigneGrille(UUID inscriptionId, String matricule, String nom, String prenom,
                              List<NoteVue> notes, BigDecimal moyenne, String mention) {
    }

    // ---------- Bulletins (SF-13 à SF-18) ----------

    public record LigneBulletin(String matiereCode, String matiereLibelle, String enseignant,
                                List<BigDecimal> notesDevoirs, BigDecimal moyenneDevoirs,
                                BigDecimal composition, BigDecimal moyenne, BigDecimal coefficient,
                                BigDecimal moyenneCoefficientee, Integer rang, boolean exAequo,
                                boolean nonClasse, String appreciation) {
    }

    public record StatistiquesClasse(BigDecimal plusForteMoyenne, BigDecimal plusFaibleMoyenne,
                                     BigDecimal moyenneClasse, int effectif) {
    }

    public record BulletinVue(UUID inscriptionId, String matricule, String nom, String prenom,
                              boolean redoublant, String classeNom, String anneeScolaire,
                              int trimestreNumero, boolean avecComposition,
                              BigDecimal poidsComposition, List<LigneBulletin> lignes,
                              BigDecimal totalCoefficients, BigDecimal totalPoints,
                              BigDecimal moyenneGenerale, Integer rang, boolean exAequo,
                              String mention, String appreciationConseil, boolean bloqueImpaye,
                              StatistiquesClasse statistiques, List<String> observations) {
    }

    public record DecisionConseil(@Size(max = 30) String mention,
                                  @Size(max = 500) String appreciationConseil) {
    }

    /** Ligne du classement général de la classe (SF-15, SF-16). */
    public record LigneClassement(UUID inscriptionId, String matricule, String nom, String prenom,
                                  BigDecimal moyenneGenerale, Integer rang, boolean exAequo,
                                  String mention, boolean bloqueImpaye) {
    }
}
