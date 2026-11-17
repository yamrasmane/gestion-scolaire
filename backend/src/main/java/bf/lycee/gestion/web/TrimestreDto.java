package bf.lycee.gestion.web;

import bf.lycee.gestion.domain.enums.Cycle;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public final class TrimestreDto {

    private TrimestreDto() {
    }

    public record Creation(
            @NotNull @Min(1) @Max(3) Integer numero,
            @NotNull LocalDate dateDebut,
            @NotNull LocalDate dateFin) {
    }

    /**
     * Réglage de la composition au niveau du trimestre. Une valeur absente
     * signifie « hériter du réglage de l'année scolaire ».
     */
    public record ReglageTrimestre(
            Boolean avecComposition,
            @DecimalMin(value = "0", inclusive = false) BigDecimal poidsComposition) {
    }

    /** Réglage propre à un cycle pour un trimestre (prioritaire sur le reste). */
    public record ReglageCycle(
            @NotNull Boolean avecComposition,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal poidsComposition) {
    }

    /** Réglage réellement appliqué, avec le niveau dont il provient : CYCLE, TRIMESTRE ou ANNEE. */
    public record CompositionEffective(boolean avecComposition, BigDecimal poidsComposition, String source) {
    }

    public record Vue(
            UUID id,
            UUID anneeScolaireId,
            Integer numero,
            LocalDate dateDebut,
            LocalDate dateFin,
            Boolean avecComposition,
            BigDecimal poidsComposition,
            boolean cloture,
            Map<Cycle, CompositionEffective> compositionParCycle) {
    }
}
