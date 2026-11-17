package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.GrilleTarifaire;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrilleTarifaireRepository extends JpaRepository<GrilleTarifaire, UUID> {

    @EntityGraph(attributePaths = {"typeFrais", "niveau", "classe"})
    List<GrilleTarifaire> findByAnneeScolaireId(UUID anneeScolaireId);

    long countByAnneeScolaireId(UUID anneeScolaireId);

    void deleteByAnneeScolaireId(UUID anneeScolaireId);
}
