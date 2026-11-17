package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.Paiement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaiementRepository extends JpaRepository<Paiement, UUID> {

    @EntityGraph(attributePaths = {"caissier"})
    List<Paiement> findByInscriptionIdOrderByDatePaiementDescIdDesc(UUID inscriptionId);

    @EntityGraph(attributePaths = {"inscription", "inscription.eleve", "caissier"})
    List<Paiement> findByJourneeCaisseOrderByIdAsc(LocalDate journeeCaisse);

    /** Dernier numéro de reçu attribué pour une année : « REC-2026-000154 ». */
    @Query("select max(p.numeroRecu) from Paiement p where p.numeroRecu like concat(:prefixe, '%')")
    String dernierNumeroRecu(@Param("prefixe") String prefixe);
}
