package bf.lycee.gestion.service;

import bf.lycee.gestion.domain.AnneeScolaire;
import bf.lycee.gestion.domain.Bulletin;
import bf.lycee.gestion.domain.EcheanceEleve;
import bf.lycee.gestion.domain.Enseignement;
import bf.lycee.gestion.domain.Evaluation;
import bf.lycee.gestion.domain.Inscription;
import bf.lycee.gestion.domain.Note;
import bf.lycee.gestion.domain.Trimestre;
import bf.lycee.gestion.repository.BulletinRepository;
import bf.lycee.gestion.repository.EnseignementRepository;
import bf.lycee.gestion.repository.EvaluationRepository;
import bf.lycee.gestion.repository.InscriptionRepository;
import bf.lycee.gestion.repository.NoteRepository;
import bf.lycee.gestion.repository.TrimestreRepository;
import bf.lycee.gestion.web.NotesDto;
import bf.lycee.gestion.web.TrimestreDto.CompositionEffective;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moyennes, classement et bulletins (SF-13 à SF-18). Les moyennes sont
 * recalculées à la demande ; la délibération fige les résultats dans la table
 * {@code bulletin}.
 */
@Service
@Transactional
public class BulletinService {

    private final EnseignementRepository enseignements;
    private final EvaluationRepository evaluations;
    private final NoteRepository notes;
    private final InscriptionRepository inscriptions;
    private final TrimestreRepository trimestres;
    private final BulletinRepository bulletins;
    private final EvaluationService evaluationService;
    private final FacturationService facturation;
    private final AppreciationService appreciations;

    public BulletinService(EnseignementRepository enseignements, EvaluationRepository evaluations,
                           NoteRepository notes, InscriptionRepository inscriptions,
                           TrimestreRepository trimestres, BulletinRepository bulletins,
                           EvaluationService evaluationService, FacturationService facturation,
                           AppreciationService appreciations) {
        this.enseignements = enseignements;
        this.evaluations = evaluations;
        this.notes = notes;
        this.inscriptions = inscriptions;
        this.trimestres = trimestres;
        this.bulletins = bulletins;
        this.evaluationService = evaluationService;
        this.facturation = facturation;
        this.appreciations = appreciations;
    }

    // ---------- API ----------

    @Transactional(readOnly = true)
    public List<NotesDto.LigneClassement> classement(UUID classeId, UUID trimestreId) {
        ResultatClasse resultat = calculer(classeId, trimestreId);
        return resultat.eleves().stream()
                .sorted(Comparator.comparing((ResultatEleve e) -> e.rang() == null ? Integer.MAX_VALUE : e.rang())
                        .thenComparing(e -> e.inscription().getEleve().getNom()))
                .map(e -> new NotesDto.LigneClassement(e.inscription().getId(),
                        e.inscription().getEleve().getMatricule(), e.inscription().getEleve().getNom(),
                        e.inscription().getEleve().getPrenom(), e.moyenneGenerale(), e.rang(),
                        e.exAequo(), resultat.bareme().mention(e.moyenneGenerale()),
                        bloqueImpaye(e.inscription())))
                .toList();
    }

    @Transactional(readOnly = true)
    public NotesDto.BulletinVue bulletin(UUID inscriptionId, UUID trimestreId) {
        Inscription inscription = inscriptions.findById(inscriptionId)
                .orElseThrow(() -> new IntrouvableException("Inscription introuvable : " + inscriptionId));
        ResultatClasse resultat = calculer(inscription.getClasse().getId(), trimestreId);
        ResultatEleve eleve = resultat.eleves().stream()
                .filter(e -> e.inscription().getId().equals(inscriptionId))
                .findFirst()
                .orElseThrow(() -> new IntrouvableException("Élève introuvable dans la classe"));
        return versVue(eleve, resultat);
    }

    /** Délibération : fige les résultats de la classe pour le trimestre (SF-17). */
    public List<NotesDto.LigneClassement> figer(UUID classeId, UUID trimestreId) {
        ResultatClasse resultat = calculer(classeId, trimestreId);
        for (ResultatEleve eleve : resultat.eleves()) {
            Bulletin bulletin = bulletins
                    .findByInscriptionIdAndTrimestreId(eleve.inscription().getId(), trimestreId)
                    .orElseGet(() -> {
                        Bulletin nouveau = new Bulletin();
                        nouveau.setInscription(eleve.inscription());
                        nouveau.setTrimestre(resultat.trimestre());
                        return nouveau;
                    });
            bulletin.setTotalCoefficients(eleve.totalCoefficients());
            bulletin.setTotalPoints(eleve.totalPoints());
            bulletin.setMoyenneGenerale(eleve.moyenneGenerale());
            bulletin.setRang(eleve.rang());
            bulletin.setExAequo(eleve.exAequo());
            bulletin.setBloqueImpaye(bloqueImpaye(eleve.inscription()));
            if (bulletin.getMention() == null) {
                bulletin.setMention(resultat.bareme().mention(eleve.moyenneGenerale()));
            }
            bulletins.save(bulletin);
        }
        return classement(classeId, trimestreId);
    }

    /** Décision du conseil de classe : mention et appréciation (SF-18). */
    public NotesDto.BulletinVue enregistrerDecision(UUID inscriptionId, UUID trimestreId,
                                                    NotesDto.DecisionConseil decision) {
        Bulletin bulletin = bulletins.findByInscriptionIdAndTrimestreId(inscriptionId, trimestreId)
                .orElseThrow(() -> new RegleMetierException(
                        "Le bulletin n'a pas encore été arrêté : lancez d'abord la délibération"));
        bulletin.setMention(decision.mention());
        bulletin.setAppreciationConseil(decision.appreciationConseil());
        return bulletin(inscriptionId, trimestreId);
    }

    // ---------- Calcul ----------

    private ResultatClasse calculer(UUID classeId, UUID trimestreId) {
        Trimestre trimestre = trimestres.findById(trimestreId)
                .orElseThrow(() -> new IntrouvableException("Trimestre introuvable : " + trimestreId));
        List<Enseignement> matieres = enseignements.findByClasseIdOrderByMatiereLibelle(classeId);
        List<Inscription> eleves = inscriptions.findByClasseIdOrderByEleveNomAscElevePrenomAsc(classeId);

        AppreciationService.Bareme bareme = appreciations.baremeDe(
                trimestre.getAnneeScolaire().getId());

        CompositionEffective composition = matieres.isEmpty()
                ? new CompositionEffective(false, BigDecimal.ONE, "ANNEE")
                : evaluationService.compositionDe(matieres.get(0), trimestre);

        // Notes du trimestre, rangées par élève puis par matière.
        Map<UUID, Map<UUID, List<Note>>> notesParEleve = new HashMap<>();
        if (!matieres.isEmpty()) {
            List<UUID> ids = matieres.stream().map(Enseignement::getId).toList();
            List<Evaluation> epreuves = evaluations.findByTrimestreIdAndEnseignementIdIn(trimestreId, ids);
            if (!epreuves.isEmpty()) {
                Map<UUID, Evaluation> parId = new HashMap<>();
                epreuves.forEach(e -> parId.put(e.getId(), e));
                for (Note note : notes.findByEvaluationIdIn(parId.keySet())) {
                    Evaluation epreuve = parId.get(note.getEvaluation().getId());
                    notesParEleve
                            .computeIfAbsent(note.getInscription().getId(), cle -> new HashMap<>())
                            .computeIfAbsent(epreuve.getEnseignement().getId(), cle -> new ArrayList<>())
                            .add(note);
                }
            }
        }

        // Moyenne de chaque matière pour chaque élève.
        Map<UUID, Map<UUID, CalculMoyennes.ResultatMatiere>> resultats = new LinkedHashMap<>();
        for (Inscription inscription : eleves) {
            Map<UUID, CalculMoyennes.ResultatMatiere> parMatiere = new LinkedHashMap<>();
            for (Enseignement enseignement : matieres) {
                List<Note> sesNotes = notesParEleve
                        .getOrDefault(inscription.getId(), Map.of())
                        .getOrDefault(enseignement.getId(), List.of());
                parMatiere.put(enseignement.getId(), CalculMoyennes.matiere(sesNotes,
                        composition.avecComposition(), composition.poidsComposition()));
            }
            resultats.put(inscription.getId(), parMatiere);
        }

        // Rang par matière (SF-15, SF-16).
        Map<UUID, Map<UUID, CalculMoyennes.Rang>> rangsParMatiere = new HashMap<>();
        for (Enseignement enseignement : matieres) {
            List<Inscription> tries = new ArrayList<>(eleves);
            tries.sort(comparateurMoyenne(resultats, enseignement.getId()));
            List<BigDecimal> moyennes = tries.stream()
                    .map(i -> resultats.get(i.getId()).get(enseignement.getId()).moyenne())
                    .toList();
            List<CalculMoyennes.Rang> rangs = CalculMoyennes.rangs(moyennes);
            Map<UUID, CalculMoyennes.Rang> parEleve = new HashMap<>();
            for (int i = 0; i < tries.size(); i++) {
                parEleve.put(tries.get(i).getId(), rangs.get(i));
            }
            rangsParMatiere.put(enseignement.getId(), parEleve);
        }

        // Moyenne générale de chaque élève (SF-14).
        List<ResultatEleve> provisoires = new ArrayList<>();
        for (Inscription inscription : eleves) {
            BigDecimal totalPoints = BigDecimal.ZERO;
            BigDecimal totalCoefficients = BigDecimal.ZERO;
            List<NotesDto.LigneBulletin> lignes = new ArrayList<>();

            for (Enseignement enseignement : matieres) {
                CalculMoyennes.ResultatMatiere resultat = resultats.get(inscription.getId())
                        .get(enseignement.getId());
                CalculMoyennes.Rang rang = rangsParMatiere.get(enseignement.getId())
                        .get(inscription.getId());
                BigDecimal moyenneCoefficientee = null;
                if (!resultat.nonClasse()) {
                    moyenneCoefficientee = resultat.moyenne().multiply(enseignement.getCoefficient())
                            .setScale(2, RoundingMode.HALF_UP);
                    totalPoints = totalPoints.add(moyenneCoefficientee);
                    totalCoefficients = totalCoefficients.add(enseignement.getCoefficient());
                }
                lignes.add(new NotesDto.LigneBulletin(
                        enseignement.getMatiere().getCode(), enseignement.getMatiere().getLibelle(),
                        enseignement.getEnseignant() == null ? null
                                : enseignement.getEnseignant().getNom() + " "
                                        + enseignement.getEnseignant().getPrenom(),
                        resultat.notesDevoirs(), resultat.moyenneDevoirs(), resultat.composition(),
                        resultat.moyenne(), enseignement.getCoefficient(), moyenneCoefficientee,
                        resultat.nonClasse() ? null : rang.valeur(),
                        !resultat.nonClasse() && rang.exAequo(), resultat.nonClasse(),
                        // L'appréciation vient du barème de l'année ; l'absence
                        // à la composition prime, car elle explique la moyenne.
                        resultat.absentComposition() ? "Absent à la composition"
                                : bareme.appreciationMatiere(resultat.moyenne())));
            }

            BigDecimal moyenneGenerale = CalculMoyennes.generale(totalPoints, totalCoefficients);
            provisoires.add(new ResultatEleve(inscription, lignes, totalCoefficients, totalPoints,
                    moyenneGenerale, null, false));
        }

        // Rang général (SF-15, SF-16).
        provisoires.sort(Comparator.comparing(ResultatEleve::moyenneGenerale,
                Comparator.nullsLast(Comparator.reverseOrder())));
        List<CalculMoyennes.Rang> rangs = CalculMoyennes.rangs(
                provisoires.stream().map(ResultatEleve::moyenneGenerale).toList());
        List<ResultatEleve> finaux = new ArrayList<>();
        for (int i = 0; i < provisoires.size(); i++) {
            ResultatEleve e = provisoires.get(i);
            finaux.add(new ResultatEleve(e.inscription(), e.lignes(), e.totalCoefficients(),
                    e.totalPoints(), e.moyenneGenerale(), rangs.get(i).valeur(), rangs.get(i).exAequo()));
        }

        List<BigDecimal> moyennes = finaux.stream()
                .map(ResultatEleve::moyenneGenerale)
                .filter(m -> m != null)
                .toList();
        NotesDto.StatistiquesClasse statistiques = new NotesDto.StatistiquesClasse(
                moyennes.isEmpty() ? null : moyennes.stream().max(BigDecimal::compareTo).orElse(null),
                moyennes.isEmpty() ? null : moyennes.stream().min(BigDecimal::compareTo).orElse(null),
                moyennes.isEmpty() ? null : CalculMoyennes.moyenne(moyennes),
                eleves.size());

        return new ResultatClasse(trimestre, composition, finaux, statistiques, bareme);
    }

    private static Comparator<Inscription> comparateurMoyenne(
            Map<UUID, Map<UUID, CalculMoyennes.ResultatMatiere>> resultats, UUID enseignementId) {
        return Comparator.comparing(
                (Inscription i) -> resultats.get(i.getId()).get(enseignementId).moyenne(),
                Comparator.nullsLast(Comparator.reverseOrder()));
    }

    /** Remise du bulletin suspendue si l'option est active et des tranches échues impayées (SF-17). */
    private boolean bloqueImpaye(Inscription inscription) {
        AnneeScolaire annee = inscription.getAnneeScolaire();
        if (!annee.isBloquerBulletinImpaye()) {
            return false;
        }
        LocalDate aujourdHui = LocalDate.now();
        for (EcheanceEleve echeance : facturation.echeancesDe(inscription.getId())) {
            if (!echeance.getDateEcheance().isAfter(aujourdHui)
                    && FacturationService.reste(echeance).signum() > 0) {
                return true;
            }
        }
        return false;
    }

    private NotesDto.BulletinVue versVue(ResultatEleve eleve, ResultatClasse resultat) {
        Inscription inscription = eleve.inscription();
        Optional<Bulletin> fige = bulletins.findByInscriptionIdAndTrimestreId(
                inscription.getId(), resultat.trimestre().getId());

        List<String> observations = new ArrayList<>();
        eleve.lignes().stream()
                .filter(l -> l.appreciation() != null)
                .forEach(l -> observations.add(l.matiereLibelle() + " : " + l.appreciation()));
        if (fige.isEmpty()) {
            observations.add("Bulletin provisoire : la délibération n'a pas encore été enregistrée.");
        }

        return new NotesDto.BulletinVue(inscription.getId(),
                inscription.getEleve().getMatricule(), inscription.getEleve().getNom(),
                inscription.getEleve().getPrenom(), inscription.isRedoublant(),
                inscription.getClasse().getNom(),
                inscription.getAnneeScolaire().getLibelle(), resultat.trimestre().getNumero(),
                resultat.composition().avecComposition(), resultat.composition().poidsComposition(),
                eleve.lignes(), eleve.totalCoefficients(), eleve.totalPoints(),
                eleve.moyenneGenerale(), eleve.rang(), eleve.exAequo(),
                fige.map(Bulletin::getMention)
                        .orElseGet(() -> resultat.bareme().mention(eleve.moyenneGenerale())),
                fige.map(Bulletin::getAppreciationConseil).orElse(null),
                bloqueImpaye(inscription), resultat.statistiques(), observations);
    }

    private record ResultatEleve(Inscription inscription, List<NotesDto.LigneBulletin> lignes,
                                 BigDecimal totalCoefficients, BigDecimal totalPoints,
                                 BigDecimal moyenneGenerale, Integer rang, boolean exAequo) {
    }

    /** Le barème voyage avec le résultat : il est lu une fois par classe. */
    private record ResultatClasse(Trimestre trimestre, CompositionEffective composition,
                                  List<ResultatEleve> eleves, NotesDto.StatistiquesClasse statistiques,
                                  AppreciationService.Bareme bareme) {
    }
}
