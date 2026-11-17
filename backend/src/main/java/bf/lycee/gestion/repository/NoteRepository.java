package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.Note;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteRepository extends JpaRepository<Note, UUID> {

    @EntityGraph(attributePaths = {"inscription", "inscription.eleve"})
    List<Note> findByEvaluationIdIn(Collection<UUID> evaluationIds);

    Optional<Note> findByEvaluationIdAndInscriptionId(UUID evaluationId, UUID inscriptionId);

    List<Note> findByEvaluationId(UUID evaluationId);

    void deleteByEvaluationId(UUID evaluationId);
}
