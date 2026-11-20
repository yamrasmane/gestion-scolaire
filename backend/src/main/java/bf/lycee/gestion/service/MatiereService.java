package bf.lycee.gestion.service;

import bf.lycee.gestion.domain.AnneeScolaire;
import bf.lycee.gestion.domain.Classe;
import bf.lycee.gestion.domain.Enseignement;
import bf.lycee.gestion.domain.Matiere;
import bf.lycee.gestion.domain.Niveau;
import bf.lycee.gestion.domain.Personnel;
import bf.lycee.gestion.domain.ProgrammeNiveau;
import bf.lycee.gestion.repository.AnneeScolaireRepository;
import bf.lycee.gestion.repository.ClasseRepository;
import bf.lycee.gestion.repository.EnseignementRepository;
import bf.lycee.gestion.repository.MatiereRepository;
import bf.lycee.gestion.repository.NiveauRepository;
import bf.lycee.gestion.repository.PersonnelRepository;
import bf.lycee.gestion.repository.ProgrammeNiveauRepository;
import bf.lycee.gestion.web.NotesDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Matières et affectation par classe : enseignant titulaire et coefficient (SF-09, SF-10). */
@Service
@Transactional
public class MatiereService {

    private final MatiereRepository matieres;
    private final EnseignementRepository enseignements;
    private final ClasseRepository classes;
    private final PersonnelRepository personnels;
    private final NiveauRepository niveaux;
    private final ProgrammeNiveauRepository programmes;
    private final AnneeScolaireRepository annees;

    public MatiereService(MatiereRepository matieres, EnseignementRepository enseignements,
                          ClasseRepository classes, PersonnelRepository personnels,
                          NiveauRepository niveaux, ProgrammeNiveauRepository programmes,
                          AnneeScolaireRepository annees) {
        this.matieres = matieres;
        this.enseignements = enseignements;
        this.classes = classes;
        this.personnels = personnels;
        this.niveaux = niveaux;
        this.programmes = programmes;
        this.annees = annees;
    }

    @Transactional(readOnly = true)
    public List<NotesDto.MatiereVue> listerMatieres() {
        return matieres.findAllByOrderByLibelle().stream()
                .map(m -> new NotesDto.MatiereVue(m.getId(), m.getCode(), m.getLibelle()))
                .toList();
    }

    public NotesDto.MatiereVue creerMatiere(NotesDto.MatiereSaisie saisie) {
        String code = saisie.code().trim().toUpperCase();
        if (matieres.existsByCodeIgnoreCase(code)) {
            throw new RegleMetierException("Une matière porte déjà le code " + code);
        }
        Matiere matiere = new Matiere();
        matiere.setCode(code);
        matiere.setLibelle(saisie.libelle().trim());
        matieres.save(matiere);
        return new NotesDto.MatiereVue(matiere.getId(), matiere.getCode(), matiere.getLibelle());
    }

    public NotesDto.MatiereVue modifierMatiere(UUID id, NotesDto.MatiereSaisie saisie) {
        Matiere matiere = matiere(id);
        String code = saisie.code().trim().toUpperCase();
        if (!matiere.getCode().equalsIgnoreCase(code) && matieres.existsByCodeIgnoreCase(code)) {
            throw new RegleMetierException("Une matière porte déjà le code " + code);
        }
        matiere.setCode(code);
        matiere.setLibelle(saisie.libelle().trim());
        return new NotesDto.MatiereVue(matiere.getId(), matiere.getCode(), matiere.getLibelle());
    }

    /** Supprime une matière, tant qu'aucun programme ni aucune classe ne l'utilise. */
    public void supprimerMatiere(UUID id) {
        Matiere matiere = matiere(id);
        if (enseignements.existsByMatiereId(id)) {
            throw new RegleMetierException(
                    "Cette matière est enseignée dans au moins une classe : retirez-la d'abord");
        }
        if (programmes.existsByMatiereId(id)) {
            throw new RegleMetierException(
                    "Cette matière figure dans le programme d'un niveau : retirez-la d'abord");
        }
        matieres.delete(matiere);
    }

    // ---------- Programme d'un niveau (SF-09) ----------

    @Transactional(readOnly = true)
    public List<NotesDto.LigneProgrammeVue> listerProgramme(UUID anneeId, UUID niveauId) {
        return programmes.findByAnneeScolaireIdAndNiveauIdOrderByMatiereLibelle(anneeId, niveauId)
                .stream()
                .map(MatiereService::versVue)
                .toList();
    }

    public NotesDto.LigneProgrammeVue ajouterAuProgramme(UUID anneeId, UUID niveauId,
                                                         NotesDto.LigneProgrammeSaisie saisie) {
        Niveau niveau = niveau(niveauId);
        if (programmes.findByAnneeScolaireIdAndNiveauIdAndMatiereId(anneeId, niveauId,
                saisie.matiereId()).isPresent()) {
            throw new RegleMetierException("Cette matière figure déjà au programme du niveau "
                    + niveau.getCode());
        }
        ProgrammeNiveau ligne = new ProgrammeNiveau();
        ligne.setAnneeScolaire(annee(anneeId));
        ligne.setNiveau(niveau);
        ligne.setMatiere(matiere(saisie.matiereId()));
        ligne.setCoefficient(saisie.coefficient());
        return versVue(programmes.save(ligne));
    }

    /**
     * Reprend le programme d'une autre année : c'est ainsi qu'une année
     * nouvelle hérite des matières et coefficients de la précédente, sans que
     * les modifications ultérieures ne remontent en arrière.
     */
    public int copierProgrammes(UUID anneeSourceId, UUID anneeCibleId) {
        AnneeScolaire cible = annee(anneeCibleId);
        List<ProgrammeNiveau> source = programmes.findByAnneeScolaireId(anneeSourceId);
        if (source.isEmpty()) {
            return 0;
        }
        programmes.deleteAll(programmes.findByAnneeScolaireId(anneeCibleId));
        programmes.flush();
        for (ProgrammeNiveau origine : source) {
            ProgrammeNiveau copie = new ProgrammeNiveau();
            copie.setAnneeScolaire(cible);
            copie.setNiveau(origine.getNiveau());
            copie.setMatiere(origine.getMatiere());
            copie.setCoefficient(origine.getCoefficient());
            programmes.save(copie);
        }
        return source.size();
    }

    public NotesDto.LigneProgrammeVue modifierLigneProgramme(UUID ligneId,
                                                             NotesDto.LigneProgrammeSaisie saisie) {
        ProgrammeNiveau ligne = programmes.findById(ligneId)
                .orElseThrow(() -> new IntrouvableException("Ligne de programme introuvable : " + ligneId));
        ligne.setMatiere(matiere(saisie.matiereId()));
        ligne.setCoefficient(saisie.coefficient());
        return versVue(ligne);
    }

    public void retirerDuProgramme(UUID ligneId) {
        ProgrammeNiveau ligne = programmes.findById(ligneId)
                .orElseThrow(() -> new IntrouvableException("Ligne de programme introuvable : " + ligneId));
        programmes.delete(ligne);
    }

    /**
     * Applique le programme du niveau aux classes de l'année : ajoute les
     * matières manquantes et aligne les coefficients. Les professeurs déjà
     * affectés et les matières ajoutées à la main sont conservés.
     */
    public NotesDto.ApplicationProgramme appliquerProgramme(UUID niveauId, UUID anneeId,
                                                            boolean alignerCoefficients) {
        Niveau niveau = niveau(niveauId);
        List<ProgrammeNiveau> programme = programmes
                .findByAnneeScolaireIdAndNiveauIdOrderByMatiereLibelle(anneeId, niveauId);
        if (programme.isEmpty()) {
            throw new RegleMetierException("Le programme du niveau " + niveau.getCode() + " est vide");
        }
        List<Classe> concernees = classes.findByAnneeScolaireIdOrderByNom(anneeId).stream()
                .filter(classe -> classe.getNiveau().getId().equals(niveauId))
                .toList();

        int ajoutees = 0;
        int misAJour = 0;
        List<String> details = new ArrayList<>();
        for (Classe classe : concernees) {
            int ajouteesClasse = 0;
            int misAJourClasse = 0;
            for (ProgrammeNiveau ligne : programme) {
                Enseignement enseignement = enseignements
                        .findByClasseIdOrderByMatiereLibelle(classe.getId()).stream()
                        .filter(e -> e.getMatiere().getId().equals(ligne.getMatiere().getId()))
                        .findFirst()
                        .orElse(null);
                if (enseignement == null) {
                    Enseignement nouveau = new Enseignement();
                    nouveau.setClasse(classe);
                    nouveau.setMatiere(ligne.getMatiere());
                    nouveau.setCoefficient(ligne.getCoefficient());
                    enseignements.save(nouveau);
                    ajouteesClasse++;
                } else if (alignerCoefficients
                        && enseignement.getCoefficient().compareTo(ligne.getCoefficient()) != 0) {
                    enseignement.setCoefficient(ligne.getCoefficient());
                    misAJourClasse++;
                }
            }
            ajoutees += ajouteesClasse;
            misAJour += misAJourClasse;
            details.add(classe.getNom() + " : " + ajouteesClasse + " matière(s) ajoutée(s)"
                    + (misAJourClasse > 0 ? ", " + misAJourClasse + " coefficient(s) aligné(s)" : ""));
        }
        return new NotesDto.ApplicationProgramme(concernees.size(), ajoutees, misAJour, details);
    }

    /** Dote une classe nouvellement créée des matières de son niveau. */
    public int doterDuProgramme(Classe classe) {
        List<ProgrammeNiveau> programme = programmes
                .findByAnneeScolaireIdAndNiveauIdOrderByMatiereLibelle(
                        classe.getAnneeScolaire().getId(), classe.getNiveau().getId());
        for (ProgrammeNiveau ligne : programme) {
            Enseignement enseignement = new Enseignement();
            enseignement.setClasse(classe);
            enseignement.setMatiere(ligne.getMatiere());
            enseignement.setCoefficient(ligne.getCoefficient());
            enseignements.save(enseignement);
        }
        return programme.size();
    }

    // ---------- Affectation des professeurs (SF-10) ----------

    /** Affecte en une fois les professeurs aux matières d'une classe. */
    public List<NotesDto.EnseignementVue> affecter(UUID classeId,
                                                   NotesDto.AffectationsSaisie saisie) {
        for (NotesDto.AffectationSaisie affectation : saisie.affectations()) {
            Enseignement enseignement = enseignements.findWithDetailsById(affectation.enseignementId())
                    .orElseThrow(() -> new IntrouvableException(
                            "Enseignement introuvable : " + affectation.enseignementId()));
            if (!enseignement.getClasse().getId().equals(classeId)) {
                throw new SaisieInvalideException("Cet enseignement n'appartient pas à la classe");
            }
            enseignement.setEnseignant(affectation.enseignantId() == null ? null
                    : enseignant(affectation.enseignantId()));
        }
        return listerEnseignements(classeId);
    }

    /** Service d'un enseignant : les matières et classes dont il a la charge. */
    @Transactional(readOnly = true)
    public NotesDto.ChargeEnseignant charge(UUID enseignantId) {
        Personnel enseignant = enseignant(enseignantId);
        List<Enseignement> liste = enseignements.findByEnseignantIdOrderByClasseNom(enseignantId);
        BigDecimal total = liste.stream()
                .map(Enseignement::getCoefficient)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long classesDistinctes = liste.stream().map(e -> e.getClasse().getId()).distinct().count();
        return new NotesDto.ChargeEnseignant(enseignant.getId(),
                enseignant.getNom() + " " + enseignant.getPrenom(), (int) classesDistinctes, total,
                liste.stream().map(MatiereService::versVue).toList());
    }

    @Transactional(readOnly = true)
    public List<NotesDto.EnseignementVue> listerEnseignements(UUID classeId) {
        return enseignements.findByClasseIdOrderByMatiereLibelle(classeId).stream()
                .map(MatiereService::versVue)
                .toList();
    }

    public NotesDto.EnseignementVue ajouterEnseignement(UUID classeId, NotesDto.EnseignementSaisie saisie) {
        Classe classe = classes.findById(classeId)
                .orElseThrow(() -> new IntrouvableException("Classe introuvable : " + classeId));
        if (enseignements.existsByClasseIdAndMatiereId(classeId, saisie.matiereId())) {
            throw new RegleMetierException("Cette matière est déjà enseignée dans la classe");
        }
        Enseignement enseignement = new Enseignement();
        enseignement.setClasse(classe);
        appliquer(enseignement, saisie);
        enseignements.save(enseignement);
        return versVue(enseignement);
    }

    public NotesDto.EnseignementVue modifierEnseignement(UUID enseignementId,
                                                         NotesDto.EnseignementSaisie saisie) {
        Enseignement enseignement = enseignements.findById(enseignementId)
                .orElseThrow(() -> new IntrouvableException("Enseignement introuvable : " + enseignementId));
        if (!enseignement.getMatiere().getId().equals(saisie.matiereId())
                && enseignements.existsByClasseIdAndMatiereId(enseignement.getClasse().getId(),
                        saisie.matiereId())) {
            throw new RegleMetierException("Cette matière est déjà enseignée dans la classe");
        }
        appliquer(enseignement, saisie);
        return versVue(enseignement);
    }

    public void supprimerEnseignement(UUID enseignementId) {
        Enseignement enseignement = enseignements.findById(enseignementId)
                .orElseThrow(() -> new IntrouvableException("Enseignement introuvable : " + enseignementId));
        enseignements.delete(enseignement);
    }

    private Matiere matiere(UUID id) {
        return matieres.findById(id)
                .orElseThrow(() -> new IntrouvableException("Matière introuvable : " + id));
    }

    private AnneeScolaire annee(UUID id) {
        return annees.findById(id)
                .orElseThrow(() -> new IntrouvableException("Année scolaire introuvable : " + id));
    }

    private Niveau niveau(UUID id) {
        return niveaux.findById(id)
                .orElseThrow(() -> new IntrouvableException("Niveau introuvable : " + id));
    }

    private Personnel enseignant(UUID id) {
        Personnel personnel = personnels.findById(id)
                .orElseThrow(() -> new IntrouvableException("Membre du personnel introuvable : " + id));
        if (!personnel.isActif()) {
            throw new RegleMetierException("La fiche de " + personnel.getNom() + " est désactivée");
        }
        return personnel;
    }

    private static NotesDto.LigneProgrammeVue versVue(ProgrammeNiveau ligne) {
        return new NotesDto.LigneProgrammeVue(ligne.getId(), ligne.getAnneeScolaire().getId(),
                ligne.getNiveau().getId(),
                ligne.getNiveau().getCode(), ligne.getNiveau().getLibelle(),
                ligne.getMatiere().getId(), ligne.getMatiere().getCode(),
                ligne.getMatiere().getLibelle(), ligne.getCoefficient());
    }

    private void appliquer(Enseignement enseignement, NotesDto.EnseignementSaisie saisie) {
        enseignement.setMatiere(matiere(saisie.matiereId()));
        enseignement.setCoefficient(saisie.coefficient());
        enseignement.setEnseignant(saisie.enseignantId() == null ? null
                : enseignant(saisie.enseignantId()));
    }

    static NotesDto.EnseignementVue versVue(Enseignement enseignement) {
        Personnel enseignant = enseignement.getEnseignant();
        return new NotesDto.EnseignementVue(enseignement.getId(),
                enseignement.getClasse().getId(), enseignement.getClasse().getNom(),
                enseignement.getMatiere().getId(), enseignement.getMatiere().getCode(),
                enseignement.getMatiere().getLibelle(),
                enseignant == null ? null : enseignant.getId(),
                enseignant == null ? null : enseignant.getNom() + " " + enseignant.getPrenom(),
                enseignement.getCoefficient());
    }
}
