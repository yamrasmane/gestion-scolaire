package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.TranchePaiement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TranchePaiementRepository extends JpaRepository<TranchePaiement, Long> {

    List<TranchePaiement> findByPlanPaiementIdOrderByNumero(Long planPaiementId);

    void deleteByPlanPaiementId(Long planPaiementId);
}
