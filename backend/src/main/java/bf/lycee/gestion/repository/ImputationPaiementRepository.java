package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.ImputationPaiement;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImputationPaiementRepository extends JpaRepository<ImputationPaiement, Long> {

    @EntityGraph(attributePaths = {"echeanceEleve", "echeanceEleve.fraisEleve", "echeanceEleve.fraisEleve.typeFrais"})
    List<ImputationPaiement> findByPaiementId(Long paiementId);

    List<ImputationPaiement> findByEcheanceEleveId(Long echeanceEleveId);
}
