package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.ConfigCompositionCycle;
import bf.lycee.gestion.domain.enums.Cycle;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigCompositionCycleRepository extends JpaRepository<ConfigCompositionCycle, Long> {

    List<ConfigCompositionCycle> findByTrimestreAnneeScolaireId(Long anneeScolaireId);

    Optional<ConfigCompositionCycle> findByTrimestreIdAndCycle(Long trimestreId, Cycle cycle);
}
