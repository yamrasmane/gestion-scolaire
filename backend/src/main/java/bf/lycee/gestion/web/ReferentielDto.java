package bf.lycee.gestion.web;

import bf.lycee.gestion.domain.Classe;
import bf.lycee.gestion.domain.Niveau;
import bf.lycee.gestion.domain.enums.Cycle;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class ReferentielDto {

    private ReferentielDto() {
    }

    public record NiveauCreation(
            @NotBlank @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9_]+", message = "lettres, chiffres et _ uniquement")
            String code,
            @NotBlank @Size(max = 100) String libelle,
            @NotNull Cycle cycle,
            @Min(0) Integer ordre) {
    }

    public record NiveauModification(
            @NotBlank @Size(max = 100) String libelle,
            @NotNull Cycle cycle,
            @NotNull @Min(0) Integer ordre) {
    }

    public record NiveauVue(UUID id, String code, String libelle, Cycle cycle, Integer ordre) {

        public static NiveauVue de(Niveau n) {
            return new NiveauVue(n.getId(), n.getCode(), n.getLibelle(), n.getCycle(), n.getOrdre());
        }
    }

    public record ClasseCreation(
            @NotNull UUID niveauId,
            @NotBlank @Size(max = 50) String nom,
            @NotNull @Min(1) Integer effectifMax) {
    }

    public record ClasseModification(
            @NotBlank @Size(max = 50) String nom,
            @NotNull @Min(1) Integer effectifMax) {
    }

    public record ClasseVue(UUID id, UUID anneeScolaireId, UUID niveauId, String niveauCode, Cycle cycle,
                            String nom, Integer effectifMax, long effectif) {

        public static ClasseVue de(Classe c, long effectif) {
            return new ClasseVue(c.getId(), c.getAnneeScolaire().getId(), c.getNiveau().getId(),
                    c.getNiveau().getCode(), c.getNiveau().getCycle(), c.getNom(), c.getEffectifMax(), effectif);
        }
    }
}
