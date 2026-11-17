package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.Matiere;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatiereRepository extends JpaRepository<Matiere, UUID> {

    List<Matiere> findAllByOrderByLibelle();

    boolean existsByCodeIgnoreCase(String code);
}
