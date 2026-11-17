package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.FraisEleve;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraisEleveRepository extends JpaRepository<FraisEleve, Long> {

    @EntityGraph(attributePaths = {"typeFrais"})
    List<FraisEleve> findByInscriptionIdOrderByTypeFraisPrioriteImputation(Long inscriptionId);

    Optional<FraisEleve> findByInscriptionIdAndTypeFraisId(Long inscriptionId, Long typeFraisId);

    boolean existsByInscriptionId(Long inscriptionId);
}
