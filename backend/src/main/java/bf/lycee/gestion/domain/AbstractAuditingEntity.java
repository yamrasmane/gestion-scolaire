package bf.lycee.gestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Socle commun à toutes les entités : identifiant, colonnes d'audit (SF-19)
 * et verrouillage optimiste. Les colonnes d'audit sont alimentées
 * automatiquement à partir du jeton Keycloak de l'utilisateur connecté.
 */
@MappedSuperclass
@Audited
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class AbstractAuditingEntity {

    /**
     * Identifiant universel (UUID) généré à l'insertion. Le style « TIME »
     * produit des valeurs croissantes dans le temps : les insertions se font
     * en fin d'index, sans la fragmentation d'un UUID purement aléatoire.
     */
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Version
    @Column(nullable = false)
    private Long version;
}
