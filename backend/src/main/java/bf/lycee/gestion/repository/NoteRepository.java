package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.Note;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteRepository extends JpaRepository<Note, Long> {

    @EntityGraph(attributePaths = {"inscription", "inscription.eleve"})
    List<Note> findByEvaluationIdIn(Collection<Long> evaluationIds);

    Optional<Note> findByEvaluationIdAndInscriptionId(Long evaluationId, Long inscriptionId);

    List<Note> findByEvaluationId(Long evaluationId);

    void deleteByEvaluationId(Long evaluationId);
}
