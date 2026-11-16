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
@Table(name = "plan_paiement")
@Audited
@Getter
@Setter
public class PlanPaiement extends AbstractAuditingEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "annee_scolaire_id", nullable = false)
    private AnneeScolaire anneeScolaire;

    @Column(nullable = false, length = 100)
    private String libelle;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "niveau_id")
    private Niveau niveau;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "classe_id")
    private Classe classe;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private CategorieEleve categorie;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ModeRepartition modeRepartition;

    @Column(nullable = false)
    private boolean parDefaut;

    @Column(precision = 12, scale = 0)
    private BigDecimal montantMinInscription;
}
