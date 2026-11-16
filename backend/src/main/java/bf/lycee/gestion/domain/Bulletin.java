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
@Table(name = "bulletin")
@Audited
@Getter
@Setter
public class Bulletin extends AbstractAuditingEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inscription_id", nullable = false)
    private Inscription inscription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trimestre_id", nullable = false)
    private Trimestre trimestre;

    @Column(precision = 6, scale = 1)
    private BigDecimal totalCoefficients;

    @Column(precision = 8, scale = 2)
    private BigDecimal totalPoints;

    @Column(precision = 4, scale = 2)
    private BigDecimal moyenneGenerale;

    private Integer rang;

    @Column(nullable = false)
    private boolean exAequo;

    @Column(length = 30)
    private String mention;

    @Column(length = 500)
    private String appreciationConseil;

    @Column(nullable = false)
    private boolean bloqueImpaye;
}
