package bf.lycee.gestion.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

/**
 * Table centrale de révision (SF-20) : une ligne par transaction auditée,
 * enrichie de l'identité Keycloak et de l'adresse IP de l'auteur.
 */
@Entity
@Table(name = "revinfo")
@RevisionEntity(KeycloakRevisionListener.class)
@Getter
@Setter
public class Revision {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "revinfo_seq")
    @SequenceGenerator(name = "revinfo_seq", sequenceName = "revinfo_seq", allocationSize = 1)
    @RevisionNumber
    @Column(name = "rev_id")
    private int id;

    @RevisionTimestamp
    @Column(name = "rev_tstmp", nullable = false)
    private long timestamp;

    @Column(name = "keycloak_user_id", length = 100)
    private String keycloakUserId;

    @Column(length = 100)
    private String username;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;
}
