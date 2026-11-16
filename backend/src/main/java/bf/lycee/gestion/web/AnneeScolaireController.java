package bf.lycee.gestion.web;

import bf.lycee.gestion.security.Roles;
import bf.lycee.gestion.service.AnneeScolaireService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/annees-scolaires")
public class AnneeScolaireController {

    private final AnneeScolaireService service;

    public AnneeScolaireController(AnneeScolaireService service) {
        this.service = service;
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
    public AnneeScolaireDto.Vue activer(@PathVariable Long id) {
        return AnneeScolaireDto.Vue.de(service.activer(id));
    }

    @GetMapping("/{id}/historique")
    @PreAuthorize(Roles.DIRECTION)
    public List<AnneeScolaireDto.Historique> historique(@PathVariable Long id) {
        return service.historique(id);
    }
}
