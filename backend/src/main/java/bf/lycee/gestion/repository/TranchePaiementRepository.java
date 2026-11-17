package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.TranchePaiement;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TranchePaiementRepository extends JpaRepository<TranchePaiement, UUID> {

    List<TranchePaiement> findByPlanPaiementIdOrderByNumero(UUID planPaiementId);

    void deleteByPlanPaiementId(UUID planPaiementId);
}
