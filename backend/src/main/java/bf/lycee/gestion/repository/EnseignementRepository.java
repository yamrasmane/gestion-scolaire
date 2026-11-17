package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.Enseignement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnseignementRepository extends JpaRepository<Enseignement, Long> {

    @EntityGraph(attributePaths = {"matiere", "enseignant", "classe"})
    List<Enseignement> findByClasseIdOrderByMatiereLibelle(Long classeId);

    @EntityGraph(attributePaths = {"matiere", "enseignant", "classe", "classe.niveau"})
    Optional<Enseignement> findWithDetailsById(Long id);

    boolean existsByClasseIdAndMatiereId(Long classeId, Long matiereId);

    List<Enseignement> findByEnseignantId(Long enseignantId);
}
