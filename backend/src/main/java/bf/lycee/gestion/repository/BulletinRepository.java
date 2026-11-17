package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.Bulletin;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BulletinRepository extends JpaRepository<Bulletin, UUID> {

    Optional<Bulletin> findByInscriptionIdAndTrimestreId(UUID inscriptionId, UUID trimestreId);

    @EntityGraph(attributePaths = {"inscription", "inscription.eleve"})
    List<Bulletin> findByTrimestreIdAndInscriptionClasseIdOrderByRang(UUID trimestreId, UUID classeId);
}
