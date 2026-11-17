package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.TypeFrais;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TypeFraisRepository extends JpaRepository<TypeFrais, Long> {

    List<TypeFrais> findAllByOrderByPrioriteImputationAscLibelleAsc();

    List<TypeFrais> findByActifTrueOrderByPrioriteImputationAscLibelleAsc();

    Optional<TypeFrais> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
