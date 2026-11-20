package bf.lycee.gestion.web;

import bf.lycee.gestion.security.Roles;
import bf.lycee.gestion.service.AnneeScolaireService;
import bf.lycee.gestion.service.ParametresAnnuelsService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/annees-scolaires")
public class AnneeScolaireController {

    private final AnneeScolaireService service;
    private final ParametresAnnuelsService parametres;

    public AnneeScolaireController(AnneeScolaireService service,
                                   ParametresAnnuelsService parametres) {
        this.service = service;
        this.parametres = parametres;
    }

    @GetMapping
    public List<AnneeScolaireDto.Vue> lister() {
        return service.lister().stream().map(AnneeScolaireDto.Vue::de).toList();
    }

    @GetMapping("/active")
    public AnneeScolaireDto.Vue active() {
        return AnneeScolaireDto.Vue.de(service.active());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.DIRECTION)
    public AnneeScolaireDto.Vue creer(@Valid @RequestBody AnneeScolaireDto.Creation saisie) {
        return AnneeScolaireDto.Vue.de(service.creer(saisie));
    }

    @PostMapping("/{id}/activation")
    @PreAuthorize(Roles.DIRECTION)
    public AnneeScolaireDto.Vue activer(@PathVariable UUID id) {
        return AnneeScolaireDto.Vue.de(service.activer(id));
    }

    /**
     * Reprend sur cette année les paramètres d'une autre : programmes des
     * niveaux, appréciations, grille tarifaire et plans de paiement. Ce que
     * l'année contenait est remplacé.
     */
    @PostMapping("/{id}/parametres/copie")
    @PreAuthorize(Roles.DIRECTION)
    public AnneeScolaireDto.CopieParametres copierParametres(@PathVariable UUID id,
                                                             @RequestParam UUID depuis) {
        return parametres.copier(depuis, id);
    }

    @GetMapping("/{id}/historique")
    @PreAuthorize(Roles.DIRECTION)
    public List<AnneeScolaireDto.Historique> historique(@PathVariable UUID id) {
        return service.historique(id);
    }
}
