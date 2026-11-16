package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.Trimestre;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrimestreRepository extends JpaRepository<Trimestre, Long> {

    List<Trimestre> findByAnneeScolaireIdOrderByNumero(Long anneeScolaireId);
}
