package bf.lycee.gestion.web;

import bf.lycee.gestion.imports.RapportImport;
import bf.lycee.gestion.security.Roles;
import bf.lycee.gestion.service.PersonnelService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import bf.lycee.gestion.domain.enums.RolePersonnel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

/** Gestion du personnel, réservée à la direction (Module 2). */
@RestController
@RequestMapping("/api/personnel")
@PreAuthorize(Roles.DIRECTION)
public class PersonnelController {

    private final PersonnelService service;

    public PersonnelController(PersonnelService service) {
        this.service = service;
    }

    /** Liste paginée du personnel : recherche libre, rôle et état du compte. */
    @GetMapping
    public PageVue<PersonnelDto.Vue> lister(
            @RequestParam(defaultValue = "") String recherche,
            @RequestParam(required = false) RolePersonnel role,
            @RequestParam(required = false) Boolean actif,
            @PageableDefault(size = 25, sort = {"nom", "prenom"}) Pageable page) {
        return service.lister(recherche, role, actif, page);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PersonnelDto.Resultat creer(@Valid @RequestBody PersonnelDto.Creation saisie) {
        return service.creer(saisie);
    }

    @PutMapping("/{id}")
    public PersonnelDto.Vue modifier(@PathVariable UUID id, @Valid @RequestBody PersonnelDto.Modification saisie) {
        return service.modifier(id, saisie);
    }

    @PostMapping("/{id}/activation")
    public PersonnelDto.Vue activer(@PathVariable UUID id) {
        return service.definirActivation(id, true);
    }

    @PostMapping("/{id}/desactivation")
    public PersonnelDto.Vue desactiver(@PathVariable UUID id) {
        return service.definirActivation(id, false);
    }

    @PostMapping("/{id}/compte")
    public PersonnelDto.Resultat creerCompte(@PathVariable UUID id) {
        return service.creerCompte(id);
    }

    /** Import Excel/CSV : {@code simulation=true} (défaut) analyse sans enregistrer. */
    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RapportImport<PersonnelDto.LigneImport> importer(
            @RequestPart("fichier") MultipartFile fichier,
            @RequestParam(defaultValue = "true") boolean simulation) {
        return service.importer(fichier, simulation);
    }
}
