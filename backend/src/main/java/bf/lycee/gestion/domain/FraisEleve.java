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
@Table(name = "frais_eleve")
@Audited
@Getter
@Setter
public class FraisEleve extends AbstractAuditingEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inscription_id", nullable = false)
    private Inscription inscription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "type_frais_id", nullable = false)
    private TypeFrais typeFrais;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal montantBrut;

    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private TypeRemise remiseType;

    @Column(precision = 12, scale = 2)
    private BigDecimal remiseValeur;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal montantRemise;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal montantNet;

    @Column(length = 255)
    private String motifRemise;

    @Column(length = 100)
    private String remiseValideePar;
}
