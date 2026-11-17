package bf.lycee.gestion.service;

import bf.lycee.gestion.domain.Classe;
import bf.lycee.gestion.domain.Enseignement;
import bf.lycee.gestion.domain.Matiere;
import bf.lycee.gestion.domain.Personnel;
import bf.lycee.gestion.repository.ClasseRepository;
import bf.lycee.gestion.repository.EnseignementRepository;
import bf.lycee.gestion.repository.MatiereRepository;
import bf.lycee.gestion.repository.PersonnelRepository;
import bf.lycee.gestion.web.NotesDto;
import java.util.List;
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

    public MatiereService(MatiereRepository matieres, EnseignementRepository enseignements,
                          ClasseRepository classes, PersonnelRepository personnels) {
        this.matieres = matieres;
        this.enseignements = enseignements;
        this.classes = classes;
        this.personnels = personnels;
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

    @Transactional(readOnly = true)
    public List<NotesDto.EnseignementVue> listerEnseignements(Long classeId) {
        return enseignements.findByClasseIdOrderByMatiereLibelle(classeId).stream()
                .map(MatiereService::versVue)
                .toList();
    }

    public NotesDto.EnseignementVue ajouterEnseignement(Long classeId, NotesDto.EnseignementSaisie saisie) {
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

    public NotesDto.EnseignementVue modifierEnseignement(Long enseignementId,
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

    public void supprimerEnseignement(Long enseignementId) {
        Enseignement enseignement = enseignements.findById(enseignementId)
                .orElseThrow(() -> new IntrouvableException("Enseignement introuvable : " + enseignementId));
        enseignements.delete(enseignement);
    }

    private void appliquer(Enseignement enseignement, NotesDto.EnseignementSaisie saisie) {
        Matiere matiere = matieres.findById(saisie.matiereId())
                .orElseThrow(() -> new IntrouvableException("Matière introuvable : " + saisie.matiereId()));
        enseignement.setMatiere(matiere);
        enseignement.setCoefficient(saisie.coefficient());
        enseignement.setEnseignant(saisie.enseignantId() == null ? null
                : personnels.findById(saisie.enseignantId())
                        .orElseThrow(() -> new IntrouvableException(
                                "Membre du personnel introuvable : " + saisie.enseignantId())));
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
