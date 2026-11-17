package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.Inscription;
import bf.lycee.gestion.domain.enums.StatutInscription;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InscriptionRepository extends JpaRepository<Inscription, UUID> {

    @EntityGraph(attributePaths = {"eleve"})
    List<Inscription> findByClasseIdOrderByEleveNomAscElevePrenomAsc(UUID classeId);

    @EntityGraph(attributePaths = {"eleve", "classe"})
    List<Inscription> findByAnneeScolaireIdAndEleveIdIn(UUID anneeScolaireId, Collection<UUID> eleveIds);

    boolean existsByEleveIdAndAnneeScolaireIdAndStatut(UUID eleveId, UUID anneeScolaireId, StatutInscription statut);

    long countByClasseIdAndStatut(UUID classeId, StatutInscription statut);

    /** Effectif (inscriptions actives) de chaque classe d'une année : [classeId, effectif]. */
    @Query("""
            select i.classe.id, count(i) from Inscription i
            where i.anneeScolaire.id = :anneeId and i.statut = :statut
            group by i.classe.id""")
    List<Object[]> effectifsParClasse(@Param("anneeId") UUID anneeId, @Param("statut") StatutInscription statut);
}
