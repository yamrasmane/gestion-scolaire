package bf.lycee.gestion.web;

import bf.lycee.gestion.domain.enums.Cycle;
import bf.lycee.gestion.security.Roles;
import bf.lycee.gestion.service.ReferentielService;
import bf.lycee.gestion.service.TrimestreService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Paramétrage : trimestres, option Composition, niveaux et classes (Module 1). */
@RestController
@RequestMapping("/api")
public class ParametrageController {

    private final TrimestreService trimestres;
    private final ReferentielService referentiel;

    public ParametrageController(TrimestreService trimestres, ReferentielService referentiel) {
        this.trimestres = trimestres;
        this.referentiel = referentiel;
    }

    // ---------- Trimestres ----------

    @GetMapping("/annees-scolaires/{anneeId}/trimestres")
    public List<TrimestreDto.Vue> trimestres(@PathVariable UUID anneeId) {
        return trimestres.lister(anneeId);
    }

    @PostMapping("/annees-scolaires/{anneeId}/trimestres")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.DIRECTION)
    public TrimestreDto.Vue creerTrimestre(@PathVariable UUID anneeId,
                                           @Valid @RequestBody TrimestreDto.Creation saisie) {
        return trimestres.creer(anneeId, saisie);
    }

    @PutMapping("/trimestres/{id}/composition")
    @PreAuthorize(Roles.DIRECTION)
    public TrimestreDto.Vue reglerComposition(@PathVariable UUID id,
                                              @Valid @RequestBody TrimestreDto.ReglageTrimestre reglage) {
        return trimestres.reglerComposition(id, reglage);
    }

    @PutMapping("/trimestres/{id}/composition/{cycle}")
    @PreAuthorize(Roles.DIRECTION)
    public TrimestreDto.Vue reglerCompositionCycle(@PathVariable UUID id, @PathVariable Cycle cycle,
                                                   @Valid @RequestBody TrimestreDto.ReglageCycle reglage) {
        return trimestres.reglerCompositionCycle(id, cycle, reglage);
    }

    @DeleteMapping("/trimestres/{id}/composition/{cycle}")
    @PreAuthorize(Roles.DIRECTION)
    public TrimestreDto.Vue supprimerReglageCycle(@PathVariable UUID id, @PathVariable Cycle cycle) {
        return trimestres.supprimerReglageCycle(id, cycle);
    }

    @PostMapping("/trimestres/{id}/cloture")
    @PreAuthorize(Roles.DIRECTION)
    public TrimestreDto.Vue cloturer(@PathVariable UUID id) {
        return trimestres.cloturer(id);
    }

    @PostMapping("/trimestres/{id}/reouverture")
    @PreAuthorize(Roles.DIRECTION)
    public TrimestreDto.Vue rouvrir(@PathVariable UUID id) {
        return trimestres.rouvrir(id);
    }

    // ---------- Niveaux ----------

    @GetMapping("/niveaux")
    public List<ReferentielDto.NiveauVue> niveaux() {
        return referentiel.listerNiveaux();
    }

    @PostMapping("/niveaux")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.DIRECTION)
    public ReferentielDto.NiveauVue creerNiveau(@Valid @RequestBody ReferentielDto.NiveauCreation saisie) {
        return referentiel.creerNiveau(saisie);
    }

    @PutMapping("/niveaux/{id}")
    @PreAuthorize(Roles.DIRECTION)
    public ReferentielDto.NiveauVue modifierNiveau(@PathVariable UUID id,
                                                   @Valid @RequestBody ReferentielDto.NiveauModification saisie) {
        return referentiel.modifierNiveau(id, saisie);
    }

    // ---------- Classes ----------

    @GetMapping("/annees-scolaires/{anneeId}/classes")
    public List<ReferentielDto.ClasseVue> classes(@PathVariable UUID anneeId) {
        return referentiel.listerClasses(anneeId);
    }

    @PostMapping("/annees-scolaires/{anneeId}/classes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.DIRECTION)
    public ReferentielDto.ClasseVue creerClasse(@PathVariable UUID anneeId,
                                                @Valid @RequestBody ReferentielDto.ClasseCreation saisie) {
        return referentiel.creerClasse(anneeId, saisie);
    }

    @PutMapping("/classes/{id}")
    @PreAuthorize(Roles.DIRECTION)
    public ReferentielDto.ClasseVue modifierClasse(@PathVariable UUID id,
                                                   @Valid @RequestBody ReferentielDto.ClasseModification saisie) {
        return referentiel.modifierClasse(id, saisie);
    }
}
