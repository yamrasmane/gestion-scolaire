package bf.lycee.gestion.web;

import bf.lycee.gestion.domain.AnneeScolaire;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class AnneeScolaireDto {

    private AnneeScolaireDto() {
    }

    /** Données saisies pour créer une année scolaire. */
    public record Creation(
            @NotBlank @Pattern(regexp = "\\d{4}-\\d{4}", message = "format attendu : 2026-2027") String libelle,
            @NotNull LocalDate dateDebut,
            @NotNull LocalDate dateFin,
            Boolean avecCompositionDefaut,
            @DecimalMin(value = "0", inclusive = false) BigDecimal poidsCompositionDefaut,
            Boolean bloquerBulletinImpaye) {
    }

    /** Représentation renvoyée par l'API. */
    public record Vue(
            UUID id,
            String libelle,
            LocalDate dateDebut,
            LocalDate dateFin,
            boolean avecCompositionDefaut,
            BigDecimal poidsCompositionDefaut,
            boolean bloquerBulletinImpaye,
            boolean actif,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy) {

        public static Vue de(AnneeScolaire a) {
            return new Vue(a.getId(), a.getLibelle(), a.getDateDebut(), a.getDateFin(),
                    a.isAvecCompositionDefaut(), a.getPoidsCompositionDefaut(),
                    a.isBloquerBulletinImpaye(), a.isActif(),
                    a.getCreatedAt(), a.getCreatedBy(), a.getUpdatedAt(), a.getUpdatedBy());
        }
    }

    /** Une ligne de la piste d'audit. */
    public record Historique(
            int revision,
            Instant date,
            String auteur,
            String adresseIp,
            String action,
            String libelle,
            boolean actif) {
    }

    /** Compte rendu d'une reprise de paramètres d'une année sur une autre. */
    public record CopieParametres(String anneeSource, int lignesProgramme, int appreciations,
                                  int tarifs, int plans) {
    }
}
