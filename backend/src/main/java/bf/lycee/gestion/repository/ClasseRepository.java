package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.Classe;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClasseRepository extends JpaRepository<Classe, UUID> {

    @EntityGraph(attributePaths = {"niveau"})
    List<Classe> findByAnneeScolaireIdOrderByNom(UUID anneeScolaireId);

    boolean existsByAnneeScolaireIdAndNomIgnoreCase(UUID anneeScolaireId, String nom);
}
