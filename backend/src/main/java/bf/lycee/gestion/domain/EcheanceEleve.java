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
@Table(name = "echeance_eleve")
@Audited
@Getter
@Setter
public class EcheanceEleve extends AbstractAuditingEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "frais_eleve_id", nullable = false)
    private FraisEleve fraisEleve;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "tranche_paiement_id")
    private TranchePaiement tranchePaiement;

    @Column(nullable = false)
    private LocalDate dateEcheance;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal montantDu;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal montantPaye;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private StatutEcheance statut;

    @Column(nullable = false)
    private boolean derogatoire;
}
