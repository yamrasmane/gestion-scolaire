package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.Classe;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClasseRepository extends JpaRepository<Classe, Long> {

    @EntityGraph(attributePaths = {"niveau"})
    List<Classe> findByAnneeScolaireIdOrderByNom(Long anneeScolaireId);

    boolean existsByAnneeScolaireIdAndNomIgnoreCase(Long anneeScolaireId, String nom);
}
