package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.Personnel;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PersonnelRepository
        extends JpaRepository<Personnel, UUID>, JpaSpecificationExecutor<Personnel> {

    List<Personnel> findAllByOrderByNomAscPrenomAsc();

    boolean existsByMatriculeIgnoreCase(String matricule);

    boolean existsByEmailIgnoreCase(String email);

    List<Personnel> findByMatriculeIn(Collection<String> matricules);

    List<Personnel> findByEmailIn(Collection<String> emails);

    Optional<Personnel> findByKeycloakId(java.util.UUID keycloakId);

    Optional<Personnel> findByEmailIgnoreCase(String email);
}
