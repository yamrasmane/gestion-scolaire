package bf.lycee.gestion.web;

import bf.lycee.gestion.domain.enums.PorteeAppreciation;
import bf.lycee.gestion.security.Roles;
import bf.lycee.gestion.service.BulletinService;
import bf.lycee.gestion.service.AppreciationService;
import bf.lycee.gestion.service.EvaluationService;
import bf.lycee.gestion.service.MatiereService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

/** Matières, évaluations, saisie des notes, classement et bulletins (Modules 4 à 7). */
@RestController
@RequestMapping("/api")
public class NotesController {

    private static final String SAISIE_NOTES = Roles.ENSEIGNANT + " or " + Roles.DIRECTION;

    private final MatiereService matieres;
    private final EvaluationService evaluations;
    private final AppreciationService appreciations;
    private final BulletinService bulletins;

    public NotesController(MatiereService matieres, EvaluationService evaluations,
                           BulletinService bulletins,
                           AppreciationService appreciations) {
        this.matieres = matieres;
        this.evaluations = evaluations;
        this.appreciations = appreciations;
        this.bulletins = bulletins;
    }

    // ---------- Matières et enseignements (SF-09, SF-10) ----------

    @GetMapping("/matieres")
    public List<NotesDto.MatiereVue> matieres() {
        return matieres.listerMatieres();
    }

    @PostMapping("/matieres")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.DIRECTION)
    public NotesDto.MatiereVue creerMatiere(@Valid @RequestBody NotesDto.MatiereSaisie saisie) {
        return matieres.creerMatiere(saisie);
    }

    @PutMapping("/matieres/{id}")
    @PreAuthorize(Roles.DIRECTION)
    public NotesDto.MatiereVue modifierMatiere(@PathVariable UUID id,
                                               @Valid @RequestBody NotesDto.MatiereSaisie saisie) {
        return matieres.modifierMatiere(id, saisie);
    }

    @DeleteMapping("/matieres/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.DIRECTION)
    public void supprimerMatiere(@PathVariable UUID id) {
        matieres.supprimerMatiere(id);
    }

    // ---------- Programme d'un niveau (SF-09) ----------

    /** Le programme appartient à une année : elle fait partie de l'adresse. */
    @GetMapping("/annees-scolaires/{anneeId}/niveaux/{niveauId}/programme")
    public List<NotesDto.LigneProgrammeVue> programme(@PathVariable UUID anneeId,
                                                      @PathVariable UUID niveauId) {
        return matieres.listerProgramme(anneeId, niveauId);
    }

    @PostMapping("/annees-scolaires/{anneeId}/niveaux/{niveauId}/programme")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.DIRECTION)
    public NotesDto.LigneProgrammeVue ajouterAuProgramme(
            @PathVariable UUID anneeId, @PathVariable UUID niveauId,
            @Valid @RequestBody NotesDto.LigneProgrammeSaisie saisie) {
        return matieres.ajouterAuProgramme(anneeId, niveauId, saisie);
    }

    @PutMapping("/programme/{ligneId}")
    @PreAuthorize(Roles.DIRECTION)
    public NotesDto.LigneProgrammeVue modifierLigneProgramme(
            @PathVariable UUID ligneId, @Valid @RequestBody NotesDto.LigneProgrammeSaisie saisie) {
        return matieres.modifierLigneProgramme(ligneId, saisie);
    }

    @DeleteMapping("/programme/{ligneId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.DIRECTION)
    public void retirerDuProgramme(@PathVariable UUID ligneId) {
        matieres.retirerDuProgramme(ligneId);
    }

    /** Applique le programme du niveau aux classes de l'année (SF-09). */
    @PostMapping("/annees-scolaires/{anneeId}/niveaux/{niveauId}/programme/application")
    @PreAuthorize(Roles.DIRECTION)
    public NotesDto.ApplicationProgramme appliquerProgramme(
            @PathVariable UUID anneeId,
            @PathVariable UUID niveauId,
            @RequestParam(defaultValue = "false") boolean alignerCoefficients) {
        return matieres.appliquerProgramme(niveauId, anneeId, alignerCoefficients);
    }

    // ---------- Appréciations du bulletin (SF-17) ----------

    /** Barème d'appréciations d'une année, pour les matières ou la moyenne générale. */
    @GetMapping("/annees-scolaires/{anneeId}/appreciations/{portee}")
    public List<NotesDto.AppreciationVue> appreciations(@PathVariable UUID anneeId,
                                                        @PathVariable PorteeAppreciation portee) {
        return appreciations.lister(anneeId, portee);
    }

    @PostMapping("/annees-scolaires/{anneeId}/appreciations/{portee}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.DIRECTION)
    public NotesDto.AppreciationVue ajouterAppreciation(
            @PathVariable UUID anneeId, @PathVariable PorteeAppreciation portee,
            @Valid @RequestBody NotesDto.AppreciationSaisie saisie) {
        return appreciations.ajouter(anneeId, portee, saisie);
    }

    @PutMapping("/appreciations/{intervalleId}")
    @PreAuthorize(Roles.DIRECTION)
    public NotesDto.AppreciationVue modifierAppreciation(
            @PathVariable UUID intervalleId,
            @Valid @RequestBody NotesDto.AppreciationSaisie saisie) {
        return appreciations.modifier(intervalleId, saisie);
    }

    @DeleteMapping("/appreciations/{intervalleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.DIRECTION)
    public void supprimerAppreciation(@PathVariable UUID intervalleId) {
        appreciations.supprimer(intervalleId);
    }

    // ---------- Affectation des professeurs (SF-10) ----------

    @PutMapping("/classes/{classeId}/affectations")
    @PreAuthorize(Roles.DIRECTION)
    public List<NotesDto.EnseignementVue> affecter(
            @PathVariable UUID classeId, @Valid @RequestBody NotesDto.AffectationsSaisie saisie) {
        return matieres.affecter(classeId, saisie);
    }

    @GetMapping("/personnel/{enseignantId}/charge")
    @PreAuthorize(Roles.DIRECTION)
    public NotesDto.ChargeEnseignant charge(@PathVariable UUID enseignantId) {
        return matieres.charge(enseignantId);
    }

    @GetMapping("/classes/{classeId}/enseignements")
    public List<NotesDto.EnseignementVue> enseignements(@PathVariable UUID classeId) {
        return matieres.listerEnseignements(classeId);
    }

    @PostMapping("/classes/{classeId}/enseignements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.DIRECTION)
    public NotesDto.EnseignementVue ajouterEnseignement(
            @PathVariable UUID classeId, @Valid @RequestBody NotesDto.EnseignementSaisie saisie) {
        return matieres.ajouterEnseignement(classeId, saisie);
    }

    @PutMapping("/enseignements/{enseignementId}")
    @PreAuthorize(Roles.DIRECTION)
    public NotesDto.EnseignementVue modifierEnseignement(
            @PathVariable UUID enseignementId, @Valid @RequestBody NotesDto.EnseignementSaisie saisie) {
        return matieres.modifierEnseignement(enseignementId, saisie);
    }

    @DeleteMapping("/enseignements/{enseignementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.DIRECTION)
    public void supprimerEnseignement(@PathVariable UUID enseignementId) {
        matieres.supprimerEnseignement(enseignementId);
    }

    // ---------- Évaluations et notes (SF-11, SF-12) ----------

    @GetMapping("/enseignements/{enseignementId}/trimestres/{trimestreId}/evaluations")
    public List<NotesDto.EvaluationVue> evaluations(@PathVariable UUID enseignementId,
                                                    @PathVariable UUID trimestreId) {
        return evaluations.lister(enseignementId, trimestreId);
    }

    @PostMapping("/enseignements/{enseignementId}/trimestres/{trimestreId}/evaluations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SAISIE_NOTES)
    public NotesDto.EvaluationVue creerEvaluation(@PathVariable UUID enseignementId,
                                                  @PathVariable UUID trimestreId,
                                                  @Valid @RequestBody NotesDto.EvaluationSaisie saisie) {
        return evaluations.creer(enseignementId, trimestreId, saisie);
    }

    /** Verrouille (ou rouvre) une épreuve : ses notes deviennent intouchables. */
    @PutMapping("/evaluations/{evaluationId}/verrou")
    public NotesDto.EvaluationVue verrouiller(@PathVariable UUID evaluationId,
                                              @RequestParam boolean verrouillee) {
        return evaluations.verrouiller(evaluationId, verrouillee);
    }

    @DeleteMapping("/evaluations/{evaluationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(SAISIE_NOTES)
    public void supprimerEvaluation(@PathVariable UUID evaluationId) {
        evaluations.supprimer(evaluationId);
    }

    @GetMapping("/enseignements/{enseignementId}/trimestres/{trimestreId}/grille")
    public NotesDto.GrilleVue grille(@PathVariable UUID enseignementId, @PathVariable UUID trimestreId) {
        return evaluations.grille(enseignementId, trimestreId);
    }

    @PutMapping("/enseignements/{enseignementId}/trimestres/{trimestreId}/grille")
    @PreAuthorize(SAISIE_NOTES)
    public NotesDto.GrilleVue enregistrerGrille(@PathVariable UUID enseignementId,
                                                @PathVariable UUID trimestreId,
                                                @Valid @RequestBody NotesDto.GrilleSaisie saisie) {
        return evaluations.enregistrer(enseignementId, trimestreId, saisie);
    }

    // ---------- Classement et bulletins (SF-15 à SF-18) ----------

    @GetMapping("/classes/{classeId}/trimestres/{trimestreId}/classement")
    public List<NotesDto.LigneClassement> classement(@PathVariable UUID classeId,
                                                     @PathVariable UUID trimestreId) {
        return bulletins.classement(classeId, trimestreId);
    }

    @GetMapping("/inscriptions/{inscriptionId}/trimestres/{trimestreId}/bulletin")
    public NotesDto.BulletinVue bulletin(@PathVariable UUID inscriptionId,
                                         @PathVariable UUID trimestreId) {
        return bulletins.bulletin(inscriptionId, trimestreId);
    }

    /** Délibération : fige les moyennes et les rangs de la classe (SF-17). */
    @PostMapping("/classes/{classeId}/trimestres/{trimestreId}/deliberation")
    @PreAuthorize(Roles.DIRECTION)
    public List<NotesDto.LigneClassement> deliberer(@PathVariable UUID classeId,
                                                    @PathVariable UUID trimestreId) {
        return bulletins.figer(classeId, trimestreId);
    }

    @PutMapping("/inscriptions/{inscriptionId}/trimestres/{trimestreId}/decision")
    @PreAuthorize(Roles.DIRECTION)
    public NotesDto.BulletinVue decision(@PathVariable UUID inscriptionId,
                                         @PathVariable UUID trimestreId,
                                         @Valid @RequestBody NotesDto.DecisionConseil decision) {
        return bulletins.enregistrerDecision(inscriptionId, trimestreId, decision);
    }
}
