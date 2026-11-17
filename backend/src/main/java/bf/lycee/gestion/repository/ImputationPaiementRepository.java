package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.ImputationPaiement;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImputationPaiementRepository extends JpaRepository<ImputationPaiement, UUID> {

    @EntityGraph(attributePaths = {"echeanceEleve", "echeanceEleve.fraisEleve", "echeanceEleve.fraisEleve.typeFrais"})
    List<ImputationPaiement> findByPaiementId(UUID paiementId);

    List<ImputationPaiement> findByEcheanceEleveId(UUID echeanceEleveId);
}
