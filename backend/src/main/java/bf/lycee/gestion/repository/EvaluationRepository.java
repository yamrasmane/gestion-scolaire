package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.Evaluation;
import bf.lycee.gestion.domain.enums.CategorieEvaluation;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationRepository extends JpaRepository<Evaluation, UUID> {

    @EntityGraph(attributePaths = {"enseignement", "enseignement.matiere"})
    List<Evaluation> findByEnseignementIdAndTrimestreIdOrderByCategorieAscLibelleAsc(
            UUID enseignementId, UUID trimestreId);

    boolean existsByEnseignementIdAndTrimestreIdAndCategorie(
            UUID enseignementId, UUID trimestreId, CategorieEvaluation categorie);

    boolean existsByEnseignementIdAndTrimestreIdAndLibelleIgnoreCase(
            UUID enseignementId, UUID trimestreId, String libelle);

    @EntityGraph(attributePaths = {"enseignement", "enseignement.matiere", "enseignement.enseignant"})
    List<Evaluation> findByTrimestreIdAndEnseignementIdIn(UUID trimestreId, Collection<UUID> enseignementIds);
}
