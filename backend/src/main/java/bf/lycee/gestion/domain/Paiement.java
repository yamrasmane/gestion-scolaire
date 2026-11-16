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
@Table(name = "paiement")
@Audited
@Getter
@Setter
public class Paiement extends AbstractAuditingEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String numeroRecu;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inscription_id", nullable = false)
    private Inscription inscription;

    @Column(nullable = false)
    private LocalDate datePaiement;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal montant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ModePaiement mode;

    @Column(length = 100)
    private String reference;

    @Column(length = 150)
    private String payeur;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "caissier_id")
    private Personnel caissier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatutPaiement statut;

    @Column(length = 255)
    private String motifAnnulation;

    @Column(length = 100)
    private String annulePar;

    @Column(nullable = false)
    private LocalDate journeeCaisse;
}
