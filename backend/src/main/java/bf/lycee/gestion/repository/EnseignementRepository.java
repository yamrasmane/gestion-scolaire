package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.Enseignement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnseignementRepository extends JpaRepository<Enseignement, UUID> {

    @EntityGraph(attributePaths = {"matiere", "enseignant", "classe"})
    List<Enseignement> findByClasseIdOrderByMatiereLibelle(UUID classeId);

    @EntityGraph(attributePaths = {"matiere", "enseignant", "classe", "classe.niveau"})
    Optional<Enseignement> findWithDetailsById(UUID id);

    boolean existsByClasseIdAndMatiereId(UUID classeId, UUID matiereId);

    List<Enseignement> findByEnseignantId(UUID enseignantId);
}
