package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.EcheanceEleve;
import bf.lycee.gestion.domain.enums.StatutEcheance;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EcheanceEleveRepository extends JpaRepository<EcheanceEleve, UUID> {

    @EntityGraph(attributePaths = {"fraisEleve", "fraisEleve.typeFrais", "tranchePaiement"})
    List<EcheanceEleve> findByFraisEleveInscriptionIdOrderByDateEcheanceAsc(UUID inscriptionId);

    List<EcheanceEleve> findByFraisEleveIdOrderByDateEcheanceAsc(UUID fraisEleveId);

    /** Échéances non soldées d'une classe, pour la liste des impayés (SF-31). */
    @EntityGraph(attributePaths = {"fraisEleve", "fraisEleve.typeFrais", "fraisEleve.inscription",
            "fraisEleve.inscription.eleve"})
    @Query("""
            select e from EcheanceEleve e
            where e.fraisEleve.inscription.classe.id in :classeIds
              and e.statut <> :payee
              and e.dateEcheance <= :limite
            order by e.fraisEleve.inscription.eleve.nom, e.dateEcheance""")
    List<EcheanceEleve> impayes(@Param("classeIds") Collection<UUID> classeIds,
                                @Param("payee") StatutEcheance payee,
                                @Param("limite") LocalDate limite);
}
