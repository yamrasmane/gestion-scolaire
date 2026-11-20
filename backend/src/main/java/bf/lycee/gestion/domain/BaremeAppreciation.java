package bf.lycee.gestion.domain;

import bf.lycee.gestion.domain.enums.PorteeAppreciation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

/**
 * Appréciation portée au bulletin pour un intervalle de moyenne, par exemple
 * « 14 à 16 : Bien ». Le barème appartient à une année scolaire : il peut
 * changer d'une année à l'autre sans toucher aux bulletins déjà édités.
 *
 * <p>L'intervalle se lit {@code [borneMin, borneMax[}, la borne haute 20 étant
 * incluse pour que 20/20 trouve son appréciation.
 */
@Entity
@Table(name = "bareme_appreciation")
@Audited
@Getter
@Setter
public class BaremeAppreciation extends AbstractAuditingEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "annee_scolaire_id", nullable = false)
    private AnneeScolaire anneeScolaire;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PorteeAppreciation portee;

    @Column(name = "borne_min", nullable = false, precision = 5, scale = 2)
    private BigDecimal borneMin;

    @Column(name = "borne_max", nullable = false, precision = 5, scale = 2)
    private BigDecimal borneMax;

    @Column(nullable = false, length = 60)
    private String libelle;

    /** Vrai si la moyenne tombe dans cet intervalle. */
    public boolean contient(BigDecimal moyenne) {
        if (moyenne == null) {
            return false;
        }
        boolean auDessusDuPlancher = moyenne.compareTo(borneMin) >= 0;
        boolean sousLePlafond = moyenne.compareTo(borneMax) < 0
                // 20/20 appartient au dernier intervalle.
                || (borneMax.compareTo(new BigDecimal("20")) == 0
                        && moyenne.compareTo(borneMax) == 0);
        return auDessusDuPlancher && sousLePlafond;
    }
}
