package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.ConfigCompositionCycle;
import bf.lycee.gestion.domain.enums.Cycle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigCompositionCycleRepository extends JpaRepository<ConfigCompositionCycle, UUID> {

    List<ConfigCompositionCycle> findByTrimestreAnneeScolaireId(UUID anneeScolaireId);

    Optional<ConfigCompositionCycle> findByTrimestreIdAndCycle(UUID trimestreId, Cycle cycle);
}
