package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.PlanPaiement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanPaiementRepository extends JpaRepository<PlanPaiement, Long> {

    @EntityGraph(attributePaths = {"niveau", "classe"})
    List<PlanPaiement> findByAnneeScolaireIdOrderByLibelle(Long anneeScolaireId);

    Optional<PlanPaiement> findByAnneeScolaireIdAndParDefautTrue(Long anneeScolaireId);

    boolean existsByAnneeScolaireIdAndLibelleIgnoreCase(Long anneeScolaireId, String libelle);
}
