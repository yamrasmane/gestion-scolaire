package bf.lycee.gestion.service;

import bf.lycee.gestion.domain.AnneeScolaire;
import bf.lycee.gestion.domain.Classe;
import bf.lycee.gestion.domain.Niveau;
import bf.lycee.gestion.domain.enums.StatutInscription;
import bf.lycee.gestion.repository.AnneeScolaireRepository;
import bf.lycee.gestion.repository.ClasseRepository;
import bf.lycee.gestion.repository.InscriptionRepository;
import bf.lycee.gestion.repository.NiveauRepository;
import bf.lycee.gestion.web.ReferentielDto.ClasseCreation;
import bf.lycee.gestion.web.ReferentielDto.ClasseModification;
import bf.lycee.gestion.web.ReferentielDto.ClasseVue;
import bf.lycee.gestion.web.ReferentielDto.NiveauCreation;
import bf.lycee.gestion.web.ReferentielDto.NiveauModification;
import bf.lycee.gestion.web.ReferentielDto.NiveauVue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Niveaux pédagogiques et classes physiques (SF-04). */
@Service
@Transactional
public class ReferentielService {

    private final NiveauRepository niveaux;
    private final ClasseRepository classes;
    private final AnneeScolaireRepository annees;
    private final InscriptionRepository inscriptions;
    private final MatiereService matieres;

    public ReferentielService(NiveauRepository niveaux, ClasseRepository classes,
                              AnneeScolaireRepository annees, InscriptionRepository inscriptions,
                              MatiereService matieres) {
        this.niveaux = niveaux;
        this.classes = classes;
        this.annees = annees;
        this.inscriptions = inscriptions;
        this.matieres = matieres;
    }

    // ---------- Niveaux ----------

    @Transactional(readOnly = true)
    public List<NiveauVue> listerNiveaux() {
        return niveaux.findAllByOrderByOrdreAscCodeAsc().stream().map(NiveauVue::de).toList();
    }

    public NiveauVue creerNiveau(NiveauCreation saisie) {
        String code = saisie.code().trim().toUpperCase();
        if (niveaux.existsByCodeIgnoreCase(code)) {
            throw new RegleMetierException("Le niveau " + code + " existe déjà");
        }
        Niveau niveau = new Niveau();
        niveau.setCode(code);
        niveau.setLibelle(saisie.libelle().trim());
        niveau.setCycle(saisie.cycle());
        niveau.setOrdre(saisie.ordre() != null ? saisie.ordre() : 0);
        return NiveauVue.de(niveaux.save(niveau));
    }

    public NiveauVue modifierNiveau(UUID id, NiveauModification saisie) {
        Niveau niveau = niveaux.findById(id)
                .orElseThrow(() -> new IntrouvableException("Niveau introuvable : " + id));
        niveau.setLibelle(saisie.libelle().trim());
        niveau.setCycle(saisie.cycle());
        niveau.setOrdre(saisie.ordre());
        return NiveauVue.de(niveaux.save(niveau));
    }

    // ---------- Classes ----------

    @Transactional(readOnly = true)
    public List<ClasseVue> listerClasses(UUID anneeId) {
        Map<UUID, Long> effectifs = new HashMap<>();
        for (Object[] ligne : inscriptions.effectifsParClasse(anneeId, StatutInscription.ACTIVE)) {
            effectifs.put((UUID) ligne[0], (Long) ligne[1]);
        }
        return classes.findByAnneeScolaireIdOrderByNom(anneeId).stream()
                .map(c -> ClasseVue.de(c, effectifs.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    public ClasseVue creerClasse(UUID anneeId, ClasseCreation saisie) {
        AnneeScolaire annee = annees.findById(anneeId)
                .orElseThrow(() -> new IntrouvableException("Année scolaire introuvable : " + anneeId));
        Niveau niveau = niveaux.findById(saisie.niveauId())
                .orElseThrow(() -> new IntrouvableException("Niveau introuvable : " + saisie.niveauId()));
        String nom = saisie.nom().trim();
        if (classes.existsByAnneeScolaireIdAndNomIgnoreCase(anneeId, nom)) {
            throw new RegleMetierException("La classe " + nom + " existe déjà pour " + annee.getLibelle());
        }
        Classe classe = new Classe();
        classe.setAnneeScolaire(annee);
        classe.setNiveau(niveau);
        classe.setNom(nom);
        classe.setEffectifMax(saisie.effectifMax());
        classes.save(classe);
        // La classe reçoit d'emblée les matières au programme de son niveau ;
        // il ne reste qu'à désigner les professeurs.
        matieres.doterDuProgramme(classe);
        return ClasseVue.de(classe, 0);
    }

    public ClasseVue modifierClasse(UUID id, ClasseModification saisie) {
        Classe classe = classes.findById(id)
                .orElseThrow(() -> new IntrouvableException("Classe introuvable : " + id));
        String nom = saisie.nom().trim();
        if (!nom.equalsIgnoreCase(classe.getNom())
                && classes.existsByAnneeScolaireIdAndNomIgnoreCase(classe.getAnneeScolaire().getId(), nom)) {
            throw new RegleMetierException("La classe " + nom + " existe déjà pour cette année");
        }
        long effectif = inscriptions.countByClasseIdAndStatut(id, StatutInscription.ACTIVE);
        if (saisie.effectifMax() < effectif) {
            throw new RegleMetierException(
                    "L'effectif maximal ne peut pas être inférieur à l'effectif actuel (" + effectif + ")");
        }
        classe.setNom(nom);
        classe.setEffectifMax(saisie.effectifMax());
        return ClasseVue.de(classes.save(classe), effectif);
    }
}
