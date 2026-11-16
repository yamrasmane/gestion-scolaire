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
@Table(name = "inscription")
@Audited
@Getter
@Setter
public class Inscription extends AbstractAuditingEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "eleve_id", nullable = false)
    private Eleve eleve;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "classe_id", nullable = false)
    private Classe classe;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "annee_scolaire_id", nullable = false)
    private AnneeScolaire anneeScolaire;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CategorieEleve categorie;

    @Column(nullable = false)
    private boolean categorieForcee;

    @Column(length = 255)
    private String motifCategorie;

    @Column(nullable = false)
    private boolean redoublant;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "plan_paiement_id")
    private PlanPaiement planPaiement;

    @Column(nullable = false)
    private LocalDate dateInscription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatutInscription statut;
}
