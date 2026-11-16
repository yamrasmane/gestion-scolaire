package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.Personnel;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonnelRepository extends JpaRepository<Personnel, Long> {

    List<Personnel> findAllByOrderByNomAscPrenomAsc();

    boolean existsByMatriculeIgnoreCase(String matricule);

    boolean existsByEmailIgnoreCase(String email);

    List<Personnel> findByMatriculeIn(Collection<String> matricules);

    List<Personnel> findByEmailIn(Collection<String> emails);
}
