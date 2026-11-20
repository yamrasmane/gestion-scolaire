package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.ProgrammeNiveau;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Le programme d'un niveau appartient à une année scolaire : tout se lit par année. */
public interface ProgrammeNiveauRepository extends JpaRepository<ProgrammeNiveau, UUID> {

    @EntityGraph(attributePaths = {"matiere", "niveau"})
    List<ProgrammeNiveau> findByAnneeScolaireIdAndNiveauIdOrderByMatiereLibelle(
            UUID anneeScolaireId, UUID niveauId);

    @EntityGraph(attributePaths = {"matiere", "niveau"})
    List<ProgrammeNiveau> findByAnneeScolaireId(UUID anneeScolaireId);

    Optional<ProgrammeNiveau> findByAnneeScolaireIdAndNiveauIdAndMatiereId(
            UUID anneeScolaireId, UUID niveauId, UUID matiereId);

    boolean existsByMatiereId(UUID matiereId);

    long countByAnneeScolaireIdAndNiveauId(UUID anneeScolaireId, UUID niveauId);
}
