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
@Table(name = "lot_impression")
@Audited
@Getter
@Setter
public class LotImpression extends AbstractAuditingEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trimestre_id", nullable = false)
    private Trimestre trimestre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private CibleLot cible;

    private UUID cibleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private FormatSortie formatSortie;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private StatutLot statut;

    @Column(nullable = false)
    private Integer progression;

    @Column(length = 500)
    private String urlFichier;
}
