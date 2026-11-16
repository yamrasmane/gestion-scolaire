package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.AnneeScolaire;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnneeScolaireRepository extends JpaRepository<AnneeScolaire, Long> {

    Optional<AnneeScolaire> findByActifTrue();

    boolean existsByLibelle(String libelle);

    List<AnneeScolaire> findAllByOrderByDateDebutDesc();
}
