package bf.lycee.gestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

/**
 * Matière enseignée à un niveau (« type de classe ») et son coefficient.
 * Appliqué à une classe, le programme crée ses enseignements (SF-09).
 */
@Entity
@Table(name = "programme_niveau")
@Audited
@Getter
@Setter
public class ProgrammeNiveau extends AbstractAuditingEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "niveau_id", nullable = false)
    private Niveau niveau;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matiere_id", nullable = false)
    private Matiere matiere;

    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal coefficient;
}
