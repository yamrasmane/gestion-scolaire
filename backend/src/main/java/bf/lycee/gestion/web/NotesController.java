package bf.lycee.gestion.web;

import bf.lycee.gestion.security.Roles;
import bf.lycee.gestion.service.BulletinService;
import bf.lycee.gestion.service.EvaluationService;
import bf.lycee.gestion.service.MatiereService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Matières, évaluations, saisie des notes, classement et bulletins (Modules 4 à 7). */
@RestController
@RequestMapping("/api")
public class NotesController {

    private static final String SAISIE_NOTES = Roles.ENSEIGNANT + " or " + Roles.DIRECTION;

    private final MatiereService matieres;
    private final EvaluationService evaluations;
    private final BulletinService bulletins;

    public NotesController(MatiereService matieres, EvaluationService evaluations,
                           BulletinService bulletins) {
        this.matieres = matieres;
        this.evaluations = evaluations;
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

    @GetMapping("/classes/{classeId}/enseignements")
    public List<NotesDto.EnseignementVue> enseignements(@PathVariable Long classeId) {
        return matieres.listerEnseignements(classeId);
    }

    @PostMapping("/classes/{classeId}/enseignements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.DIRECTION)
    public NotesDto.EnseignementVue ajouterEnseignement(
            @PathVariable Long classeId, @Valid @RequestBody NotesDto.EnseignementSaisie saisie) {
        return matieres.ajouterEnseignement(classeId, saisie);
    }

    @PutMapping("/enseignements/{enseignementId}")
    @PreAuthorize(Roles.DIRECTION)
    public NotesDto.EnseignementVue modifierEnseignement(
            @PathVariable Long enseignementId, @Valid @RequestBody NotesDto.EnseignementSaisie saisie) {
        return matieres.modifierEnseignement(enseignementId, saisie);
    }

    @DeleteMapping("/enseignements/{enseignementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.DIRECTION)
    public void supprimerEnseignement(@PathVariable Long enseignementId) {
        matieres.supprimerEnseignement(enseignementId);
    }

    // ---------- Évaluations et notes (SF-11, SF-12) ----------

    @GetMapping("/enseignements/{enseignementId}/trimestres/{trimestreId}/evaluations")
    public List<NotesDto.EvaluationVue> evaluations(@PathVariable Long enseignementId,
                                                    @PathVariable Long trimestreId) {
        return evaluations.lister(enseignementId, trimestreId);
    }

    @PostMapping("/enseignements/{enseignementId}/trimestres/{trimestreId}/evaluations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SAISIE_NOTES)
    public NotesDto.EvaluationVue creerEvaluation(@PathVariable Long enseignementId,
                                                  @PathVariable Long trimestreId,
                                                  @Valid @RequestBody NotesDto.EvaluationSaisie saisie) {
        return evaluations.creer(enseignementId, trimestreId, saisie);
    }

    @DeleteMapping("/evaluations/{evaluationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(SAISIE_NOTES)
    public void supprimerEvaluation(@PathVariable Long evaluationId) {
        evaluations.supprimer(evaluationId);
    }

    @GetMapping("/enseignements/{enseignementId}/trimestres/{trimestreId}/grille")
    public NotesDto.GrilleVue grille(@PathVariable Long enseignementId, @PathVariable Long trimestreId) {
        return evaluations.grille(enseignementId, trimestreId);
    }

    @PutMapping("/enseignements/{enseignementId}/trimestres/{trimestreId}/grille")
    @PreAuthorize(SAISIE_NOTES)
    public NotesDto.GrilleVue enregistrerGrille(@PathVariable Long enseignementId,
                                                @PathVariable Long trimestreId,
                                                @Valid @RequestBody NotesDto.GrilleSaisie saisie) {
        return evaluations.enregistrer(enseignementId, trimestreId, saisie);
    }

    // ---------- Classement et bulletins (SF-15 à SF-18) ----------

    @GetMapping("/classes/{classeId}/trimestres/{trimestreId}/classement")
    public List<NotesDto.LigneClassement> classement(@PathVariable Long classeId,
                                                     @PathVariable Long trimestreId) {
        return bulletins.classement(classeId, trimestreId);
    }

    @GetMapping("/inscriptions/{inscriptionId}/trimestres/{trimestreId}/bulletin")
    public NotesDto.BulletinVue bulletin(@PathVariable Long inscriptionId,
                                         @PathVariable Long trimestreId) {
        return bulletins.bulletin(inscriptionId, trimestreId);
    }

    /** Délibération : fige les moyennes et les rangs de la classe (SF-17). */
    @PostMapping("/classes/{classeId}/trimestres/{trimestreId}/deliberation")
    @PreAuthorize(Roles.DIRECTION)
    public List<NotesDto.LigneClassement> deliberer(@PathVariable Long classeId,
                                                    @PathVariable Long trimestreId) {
        return bulletins.figer(classeId, trimestreId);
    }

    @PutMapping("/inscriptions/{inscriptionId}/trimestres/{trimestreId}/decision")
    @PreAuthorize(Roles.DIRECTION)
    public NotesDto.BulletinVue decision(@PathVariable Long inscriptionId,
                                         @PathVariable Long trimestreId,
                                         @Valid @RequestBody NotesDto.DecisionConseil decision) {
        return bulletins.enregistrerDecision(inscriptionId, trimestreId, decision);
    }
}
