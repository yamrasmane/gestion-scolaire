package bf.lycee.gestion.service;

import bf.lycee.gestion.domain.ConfigCompositionCycle;
import bf.lycee.gestion.domain.Enseignement;
import bf.lycee.gestion.domain.Evaluation;
import bf.lycee.gestion.domain.Inscription;
import bf.lycee.gestion.domain.Note;
import bf.lycee.gestion.domain.Trimestre;
import bf.lycee.gestion.domain.enums.CategorieEvaluation;
import bf.lycee.gestion.repository.ConfigCompositionCycleRepository;
import bf.lycee.gestion.repository.EnseignementRepository;
import bf.lycee.gestion.repository.EvaluationRepository;
import bf.lycee.gestion.repository.InscriptionRepository;
import bf.lycee.gestion.repository.NoteRepository;
import bf.lycee.gestion.repository.TrimestreRepository;
import bf.lycee.gestion.web.NotesDto;
import bf.lycee.gestion.web.TrimestreDto.CompositionEffective;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Évaluations d'un enseignement et grille de saisie des notes (SF-11, SF-12). */
@Service
@Transactional
public class EvaluationService {

    private final EvaluationRepository evaluations;
    private final NoteRepository notes;
    private final EnseignementRepository enseignements;
    private final TrimestreRepository trimestres;
    private final ConfigCompositionCycleRepository configsCycle;
    private final InscriptionRepository inscriptions;
    private final ComptePersonnelService comptes;

    public EvaluationService(EvaluationRepository evaluations, NoteRepository notes,
                             EnseignementRepository enseignements, TrimestreRepository trimestres,
                             ConfigCompositionCycleRepository configsCycle,
                             InscriptionRepository inscriptions, ComptePersonnelService comptes) {
        this.evaluations = evaluations;
        this.notes = notes;
        this.enseignements = enseignements;
        this.trimestres = trimestres;
        this.configsCycle = configsCycle;
        this.inscriptions = inscriptions;
        this.comptes = comptes;
    }

    // ---------- Évaluations ----------

    @Transactional(readOnly = true)
    public List<NotesDto.EvaluationVue> lister(UUID enseignementId, UUID trimestreId) {
        return evaluations
                .findByEnseignementIdAndTrimestreIdOrderByCategorieAscLibelleAsc(enseignementId, trimestreId)
                .stream().map(EvaluationService::versVue).toList();
    }

    public NotesDto.EvaluationVue creer(UUID enseignementId, UUID trimestreId,
                                        NotesDto.EvaluationSaisie saisie) {
        Enseignement enseignement = enseignement(enseignementId);
        Trimestre trimestre = trimestre(trimestreId);
        verifierAcces(enseignement);
        verifierOuvert(trimestre);

        if (saisie.categorie() == CategorieEvaluation.COMPOSITION) {
            CompositionEffective composition = compositionDe(enseignement, trimestre);
            if (!composition.avecComposition()) {
                throw new RegleMetierException(
                        "La composition n'est pas activée pour ce trimestre et ce cycle");
            }
            if (evaluations.existsByEnseignementIdAndTrimestreIdAndCategorie(
                    enseignementId, trimestreId, CategorieEvaluation.COMPOSITION)) {
                throw new RegleMetierException(
                        "Une composition existe déjà pour cette matière et ce trimestre");
            }
        }
        if (evaluations.existsByEnseignementIdAndTrimestreIdAndLibelleIgnoreCase(
                enseignementId, trimestreId, saisie.libelle().trim())) {
            throw new RegleMetierException("Une épreuve porte déjà ce libellé dans ce trimestre");
        }

        Evaluation evaluation = new Evaluation();
        evaluation.setEnseignement(enseignement);
        evaluation.setTrimestre(trimestre);
        evaluation.setCategorie(saisie.categorie());
        evaluation.setLibelle(saisie.libelle().trim());
        evaluation.setDateEval(saisie.dateEval());
        evaluation.setBareme(saisie.bareme() == null ? CalculMoyennes.VINGT : saisie.bareme());
        evaluations.save(evaluation);
        return versVue(evaluation);
    }

    public void supprimer(UUID evaluationId) {
        Evaluation evaluation = evaluation(evaluationId);
        verifierAcces(evaluation.getEnseignement());
        verifierOuvert(evaluation.getTrimestre());
        notes.deleteAll(notes.findByEvaluationId(evaluationId));
        notes.flush();
        evaluations.delete(evaluation);
    }

    // ---------- Grille de saisie (SF-12) ----------

    @Transactional(readOnly = true)
    public NotesDto.GrilleVue grille(UUID enseignementId, UUID trimestreId) {
        Enseignement enseignement = enseignement(enseignementId);
        Trimestre trimestre = trimestre(trimestreId);
        CompositionEffective composition = compositionDe(enseignement, trimestre);

        List<Evaluation> epreuves = evaluations
                .findByEnseignementIdAndTrimestreIdOrderByCategorieAscLibelleAsc(enseignementId, trimestreId);
        Map<UUID, List<Note>> notesParEleve = notesParInscription(epreuves);

        List<NotesDto.LigneGrille> lignes = new ArrayList<>();
        for (Inscription inscription : inscriptions
                .findByClasseIdOrderByEleveNomAscElevePrenomAsc(enseignement.getClasse().getId())) {
            List<Note> sesNotes = notesParEleve.getOrDefault(inscription.getId(), List.of());
            Map<UUID, Note> parEvaluation = new HashMap<>();
            sesNotes.forEach(n -> parEvaluation.put(n.getEvaluation().getId(), n));

            List<NotesDto.NoteVue> vues = epreuves.stream()
                    .map(e -> {
                        Note note = parEvaluation.get(e.getId());
                        return new NotesDto.NoteVue(inscription.getId(), e.getId(),
                                note == null ? null : note.getValeur(),
                                note != null && note.isAbsent(),
                                note == null ? null : note.getAppreciation());
                    })
                    .toList();

            CalculMoyennes.ResultatMatiere resultat = CalculMoyennes.matiere(
                    sesNotes, composition.avecComposition(), composition.poidsComposition());
            lignes.add(new NotesDto.LigneGrille(inscription.getId(),
                    inscription.getEleve().getMatricule(), inscription.getEleve().getNom(),
                    inscription.getEleve().getPrenom(), vues, resultat.moyenne(),
                    resultat.absentComposition() ? "Absent à la composition" : null));
        }

        return new NotesDto.GrilleVue(enseignementId, enseignement.getMatiere().getLibelle(),
                enseignement.getClasse().getNom(), trimestreId, composition.avecComposition(),
                composition.poidsComposition(), trimestre.isCloture(),
                epreuves.stream().map(EvaluationService::versVue).toList(), lignes);
    }

    /** Enregistre la grille : une note par élève et par épreuve (SF-12). */
    public NotesDto.GrilleVue enregistrer(UUID enseignementId, UUID trimestreId,
                                          NotesDto.GrilleSaisie saisie) {
        Enseignement enseignement = enseignement(enseignementId);
        Trimestre trimestre = trimestre(trimestreId);
        verifierAcces(enseignement);
        verifierOuvert(trimestre);

        for (NotesDto.NoteSaisie ligne : saisie.notes()) {
            Evaluation evaluation = evaluation(ligne.evaluationId());
            if (!evaluation.getEnseignement().getId().equals(enseignementId)
                    || !evaluation.getTrimestre().getId().equals(trimestreId)) {
                throw new SaisieInvalideException("Épreuve étrangère à cette grille de saisie");
            }
            if (ligne.valeur() != null && ligne.valeur().compareTo(evaluation.getBareme()) > 0) {
                throw new SaisieInvalideException("La note dépasse le barème de « "
                        + evaluation.getLibelle() + " » (" + evaluation.getBareme() + ")");
            }
            Inscription inscription = inscriptions.findById(ligne.inscriptionId())
                    .orElseThrow(() -> new IntrouvableException(
                            "Inscription introuvable : " + ligne.inscriptionId()));
            if (!inscription.getClasse().getId().equals(enseignement.getClasse().getId())) {
                throw new SaisieInvalideException("Cet élève n'est pas inscrit dans la classe");
            }

            Note note = notes.findByEvaluationIdAndInscriptionId(ligne.evaluationId(), ligne.inscriptionId())
                    .orElseGet(() -> {
                        Note nouvelle = new Note();
                        nouvelle.setEvaluation(evaluation);
                        nouvelle.setInscription(inscription);
                        return nouvelle;
                    });
            note.setValeur(ligne.absent() ? null : ligne.valeur());
            note.setAbsent(ligne.absent());
            note.setAppreciation(ligne.appreciation());
            notes.save(note);
        }
        notes.flush();
        return grille(enseignementId, trimestreId);
    }

    // ---------- Outils ----------

    Map<UUID, List<Note>> notesParInscription(List<Evaluation> epreuves) {
        Map<UUID, List<Note>> parEleve = new HashMap<>();
        if (epreuves.isEmpty()) {
            return parEleve;
        }
        List<UUID> ids = epreuves.stream().map(Evaluation::getId).toList();
        for (Note note : notes.findByEvaluationIdIn(ids)) {
            parEleve.computeIfAbsent(note.getInscription().getId(), cle -> new ArrayList<>()).add(note);
        }
        return parEleve;
    }

    CompositionEffective compositionDe(Enseignement enseignement, Trimestre trimestre) {
        List<ConfigCompositionCycle> configs = configsCycle
                .findByTrimestreAnneeScolaireId(trimestre.getAnneeScolaire().getId());
        return TrimestreService.compositionEffective(trimestre,
                enseignement.getClasse().getNiveau().getCycle(), configs);
    }

    /** Un enseignant ne saisit que dans ses propres classes ; la direction partout. */
    void verifierAcces(Enseignement enseignement) {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification == null) {
            return;
        }
        boolean direction = authentification.getAuthorities().stream()
                .anyMatch(autorite -> "ROLE_DIRECTEUR".equals(autorite.getAuthority())
                        || "ROLE_CENSEUR".equals(autorite.getAuthority()));
        if (direction) {
            return;
        }
        UUID titulaire = enseignement.getEnseignant() == null ? null : enseignement.getEnseignant().getId();
        UUID connecte = comptes.connecte().map(personnel -> personnel.getId()).orElse(null);
        if (titulaire == null || !titulaire.equals(connecte)) {
            throw new RegleMetierException("Vous n'êtes pas le professeur titulaire de cette matière");
        }
    }

    private static void verifierOuvert(Trimestre trimestre) {
        if (trimestre.isCloture()) {
            throw new RegleMetierException("Le trimestre " + trimestre.getNumero()
                    + " est clôturé : les notes ne sont plus modifiables");
        }
    }

    private Enseignement enseignement(UUID id) {
        return enseignements.findWithDetailsById(id)
                .orElseThrow(() -> new IntrouvableException("Enseignement introuvable : " + id));
    }

    private Trimestre trimestre(UUID id) {
        return trimestres.findById(id)
                .orElseThrow(() -> new IntrouvableException("Trimestre introuvable : " + id));
    }

    private Evaluation evaluation(UUID id) {
        return evaluations.findById(id)
                .orElseThrow(() -> new IntrouvableException("Épreuve introuvable : " + id));
    }

    static NotesDto.EvaluationVue versVue(Evaluation evaluation) {
        return new NotesDto.EvaluationVue(evaluation.getId(), evaluation.getEnseignement().getId(),
                evaluation.getTrimestre().getId(), evaluation.getCategorie(), evaluation.getLibelle(),
                evaluation.getDateEval(), evaluation.getBareme());
    }

    /** Barème par défaut, pour les épreuves créées sans précision. */
    static BigDecimal baremeParDefaut() {
        return CalculMoyennes.VINGT;
    }
}
