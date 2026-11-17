package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.PlanPaiement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanPaiementRepository extends JpaRepository<PlanPaiement, UUID> {

    @EntityGraph(attributePaths = {"niveau", "classe"})
    List<PlanPaiement> findByAnneeScolaireIdOrderByLibelle(UUID anneeScolaireId);

    Optional<PlanPaiement> findByAnneeScolaireIdAndParDefautTrue(UUID anneeScolaireId);

    boolean existsByAnneeScolaireIdAndLibelleIgnoreCase(UUID anneeScolaireId, String libelle);
}
