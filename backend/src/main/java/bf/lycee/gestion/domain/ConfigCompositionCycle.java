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
@Table(name = "config_composition_cycle")
@Audited
@Getter
@Setter
public class ConfigCompositionCycle extends AbstractAuditingEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trimestre_id", nullable = false)
    private Trimestre trimestre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Cycle cycle;

    @Column(nullable = false)
    private boolean avecComposition;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal poidsComposition;
}
