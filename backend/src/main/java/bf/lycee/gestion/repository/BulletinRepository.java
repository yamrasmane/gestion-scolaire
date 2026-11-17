package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.Bulletin;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BulletinRepository extends JpaRepository<Bulletin, Long> {

    Optional<Bulletin> findByInscriptionIdAndTrimestreId(Long inscriptionId, Long trimestreId);

    @EntityGraph(attributePaths = {"inscription", "inscription.eleve"})
    List<Bulletin> findByTrimestreIdAndInscriptionClasseIdOrderByRang(Long trimestreId, Long classeId);
}
