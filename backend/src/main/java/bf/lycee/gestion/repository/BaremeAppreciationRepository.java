package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.BaremeAppreciation;
import bf.lycee.gestion.domain.enums.PorteeAppreciation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BaremeAppreciationRepository extends JpaRepository<BaremeAppreciation, UUID> {

    List<BaremeAppreciation> findByAnneeScolaireIdAndPorteeOrderByBorneMin(
            UUID anneeScolaireId, PorteeAppreciation portee);

    List<BaremeAppreciation> findByAnneeScolaireId(UUID anneeScolaireId);

    boolean existsByAnneeScolaireId(UUID anneeScolaireId);
}
