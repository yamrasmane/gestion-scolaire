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
@Table(name = "tranche_paiement")
@Audited
@Getter
@Setter
public class TranchePaiement extends AbstractAuditingEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_paiement_id", nullable = false)
    private PlanPaiement planPaiement;

    @Column(nullable = false)
    private Integer numero;

    @Column(nullable = false, length = 100)
    private String libelle;

    @Column(nullable = false)
    private LocalDate dateEcheance;

    @Column(precision = 5, scale = 2)
    private BigDecimal pourcentage;

    @Column(precision = 12, scale = 0)
    private BigDecimal montant;
}
