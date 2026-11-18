package bf.lycee.gestion.web;

import bf.lycee.gestion.imports.RapportImport;
import bf.lycee.gestion.security.Roles;
import bf.lycee.gestion.service.EleveService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import bf.lycee.gestion.domain.enums.Sexe;
import bf.lycee.gestion.domain.enums.StatutInscription;
import java.time.LocalDate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Élèves et inscriptions (Module 3). */
@RestController
@RequestMapping("/api")
public class EleveController {

    private final EleveService service;

    public EleveController(EleveService service) {
        this.service = service;
    }

    /**
     * Liste paginée des élèves. Critères cumulables : recherche libre
     * (matricule, nom, prénom, parent), sexe et intervalle de naissance.
     */
    @GetMapping("/eleves")
    public PageVue<EleveDto.EleveVue> rechercher(
            @RequestParam(defaultValue = "") String recherche,
            @RequestParam(required = false) Sexe sexe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate neApres,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate neAvant,
            @PageableDefault(size = 25, sort = {"nom", "prenom"}) Pageable page) {
        return service.rechercher(recherche, sexe, neApres, neAvant, page);
    }

    /** Liste paginée des inscriptions d'une classe, avec recherche et filtres. */
    @GetMapping("/classes/{classeId}/inscriptions")
    public PageVue<EleveDto.InscriptionVue> inscriptions(
            @PathVariable UUID classeId,
            @RequestParam(defaultValue = "") String recherche,
            @RequestParam(required = false) StatutInscription statut,
            @RequestParam(required = false) Boolean redoublant,
            @PageableDefault(size = 25, sort = {"eleve.nom", "eleve.prenom"}) Pageable page) {
        return service.inscriptionsDeLaClasse(classeId, recherche, statut, redoublant, page);
    }

    @PostMapping("/classes/{classeId}/inscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.SCOLARITE)
    public EleveDto.InscriptionVue inscrire(@PathVariable UUID classeId,
                                            @Valid @RequestBody EleveDto.InscriptionManuelle saisie) {
        return service.inscrire(classeId, saisie);
    }

    /** Import Excel/CSV de la liste d'une classe : {@code simulation=true} (défaut) analyse sans enregistrer. */
    @PostMapping(path = "/classes/{classeId}/inscriptions/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Roles.SCOLARITE)
    public RapportImport<EleveDto.LigneImport> importer(
            @PathVariable UUID classeId,
            @RequestPart("fichier") MultipartFile fichier,
            @RequestParam(defaultValue = "true") boolean simulation) {
        return service.importer(classeId, fichier, simulation);
    }

    @PutMapping("/inscriptions/{id}/categorie")
    @PreAuthorize(Roles.INTENDANT)
    public EleveDto.InscriptionVue corrigerCategorie(@PathVariable UUID id,
                                                     @Valid @RequestBody EleveDto.CorrectionCategorie correction) {
        return service.corrigerCategorie(id, correction);
    }
}
