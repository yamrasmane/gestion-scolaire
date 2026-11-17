package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.Evaluation;
import bf.lycee.gestion.domain.enums.CategorieEvaluation;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {

    @EntityGraph(attributePaths = {"enseignement", "enseignement.matiere"})
    List<Evaluation> findByEnseignementIdAndTrimestreIdOrderByCategorieAscLibelleAsc(
            Long enseignementId, Long trimestreId);

    boolean existsByEnseignementIdAndTrimestreIdAndCategorie(
            Long enseignementId, Long trimestreId, CategorieEvaluation categorie);

    boolean existsByEnseignementIdAndTrimestreIdAndLibelleIgnoreCase(
            Long enseignementId, Long trimestreId, String libelle);

    @EntityGraph(attributePaths = {"enseignement", "enseignement.matiere", "enseignement.enseignant"})
    List<Evaluation> findByTrimestreIdAndEnseignementIdIn(Long trimestreId, Collection<Long> enseignementIds);
}
