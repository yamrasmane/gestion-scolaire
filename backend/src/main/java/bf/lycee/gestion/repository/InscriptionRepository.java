package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.Inscription;
import bf.lycee.gestion.domain.enums.StatutInscription;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InscriptionRepository extends JpaRepository<Inscription, Long> {

    @EntityGraph(attributePaths = {"eleve"})
    List<Inscription> findByClasseIdOrderByEleveNomAscElevePrenomAsc(Long classeId);

    @EntityGraph(attributePaths = {"eleve", "classe"})
    List<Inscription> findByAnneeScolaireIdAndEleveIdIn(Long anneeScolaireId, Collection<Long> eleveIds);

    boolean existsByEleveIdAndAnneeScolaireIdAndStatut(Long eleveId, Long anneeScolaireId, StatutInscription statut);

    long countByClasseIdAndStatut(Long classeId, StatutInscription statut);

    /** Effectif (inscriptions actives) de chaque classe d'une année : [classeId, effectif]. */
    @Query("""
            select i.classe.id, count(i) from Inscription i
            where i.anneeScolaire.id = :anneeId and i.statut = :statut
            group by i.classe.id""")
    List<Object[]> effectifsParClasse(@Param("anneeId") Long anneeId, @Param("statut") StatutInscription statut);
}
