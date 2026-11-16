package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.AnneeScolaire;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnneeScolaireRepository extends JpaRepository<AnneeScolaire, Long> {

    Optional<AnneeScolaire> findByActifTrue();

    boolean existsByLibelle(String libelle);

    List<AnneeScolaire> findAllByOrderByDateDebutDesc();

    /** Année scolaire qui précède immédiatement une date de début donnée. */
    Optional<AnneeScolaire> findFirstByDateDebutBeforeOrderByDateDebutDesc(LocalDate dateDebut);
}
