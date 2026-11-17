package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.Trimestre;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrimestreRepository extends JpaRepository<Trimestre, UUID> {

    List<Trimestre> findByAnneeScolaireIdOrderByNumero(UUID anneeScolaireId);
}
