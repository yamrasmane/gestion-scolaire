package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.FraisEleve;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraisEleveRepository extends JpaRepository<FraisEleve, UUID> {

    @EntityGraph(attributePaths = {"typeFrais"})
    List<FraisEleve> findByInscriptionIdOrderByTypeFraisPrioriteImputation(UUID inscriptionId);

    Optional<FraisEleve> findByInscriptionIdAndTypeFraisId(UUID inscriptionId, UUID typeFraisId);

    boolean existsByInscriptionId(UUID inscriptionId);
}
