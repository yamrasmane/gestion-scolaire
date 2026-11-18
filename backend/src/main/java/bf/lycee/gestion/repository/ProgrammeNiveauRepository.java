package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.ProgrammeNiveau;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgrammeNiveauRepository extends JpaRepository<ProgrammeNiveau, UUID> {

    @EntityGraph(attributePaths = {"matiere", "niveau"})
    List<ProgrammeNiveau> findByNiveauIdOrderByMatiereLibelle(UUID niveauId);

    Optional<ProgrammeNiveau> findByNiveauIdAndMatiereId(UUID niveauId, UUID matiereId);

    boolean existsByMatiereId(UUID matiereId);

    long countByNiveauId(UUID niveauId);
}
