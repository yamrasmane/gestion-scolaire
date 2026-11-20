package bf.lycee.gestion.domain;

import bf.lycee.gestion.domain.enums.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "evaluation")
@Audited
@Getter
@Setter
public class Evaluation extends AbstractAuditingEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enseignement_id", nullable = false)
    private Enseignement enseignement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trimestre_id", nullable = false)
    private Trimestre trimestre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private CategorieEvaluation categorie;

    @Column(nullable = false, length = 50)
    private String libelle;

    private LocalDate dateEval;

    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal bareme;

    /**
     * Épreuve verrouillée : ses notes ne peuvent plus être modifiées ni
     * complétées tant que la direction ne l'a pas déverrouillée.
     */
    @Column(nullable = false)
    private boolean verrouillee;
}
