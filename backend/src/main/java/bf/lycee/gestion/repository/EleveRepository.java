package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.Eleve;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EleveRepository extends JpaRepository<Eleve, UUID> {

    Optional<Eleve> findByMatricule(String matricule);

    List<Eleve> findByMatriculeIn(Collection<String> matricules);

    @Query("""
            select e from Eleve e
            where lower(e.nom) like lower(concat('%', :terme, '%'))
               or lower(e.prenom) like lower(concat('%', :terme, '%'))
               or lower(e.matricule) like lower(concat('%', :terme, '%'))
            order by e.nom, e.prenom""")
    List<Eleve> rechercher(@Param("terme") String terme, Pageable page);
}
