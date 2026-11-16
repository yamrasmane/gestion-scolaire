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
@Table(name = "annee_scolaire")
@Audited
@Getter
@Setter
public class AnneeScolaire extends AbstractAuditingEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String libelle;

    @Column(nullable = false)
    private LocalDate dateDebut;

    @Column(nullable = false)
    private LocalDate dateFin;

    @Column(nullable = false)
    private boolean avecCompositionDefaut;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal poidsCompositionDefaut;

    @Column(nullable = false)
    private boolean bloquerBulletinImpaye;

    @Column(nullable = false)
    private boolean actif;
}
