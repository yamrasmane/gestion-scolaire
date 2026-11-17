package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.Niveau;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NiveauRepository extends JpaRepository<Niveau, UUID> {

    List<Niveau> findAllByOrderByOrdreAscCodeAsc();

    boolean existsByCodeIgnoreCase(String code);
}
