package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.GrilleTarifaire;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrilleTarifaireRepository extends JpaRepository<GrilleTarifaire, Long> {

    @EntityGraph(attributePaths = {"typeFrais", "niveau", "classe"})
    List<GrilleTarifaire> findByAnneeScolaireId(Long anneeScolaireId);

    long countByAnneeScolaireId(Long anneeScolaireId);

    void deleteByAnneeScolaireId(Long anneeScolaireId);
}
