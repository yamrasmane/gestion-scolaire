package bf.lycee.gestion.service;

import bf.lycee.gestion.domain.AnneeScolaire;
import bf.lycee.gestion.domain.Classe;
import bf.lycee.gestion.domain.Eleve;
import bf.lycee.gestion.domain.Inscription;
import bf.lycee.gestion.domain.enums.CategorieEleve;
import bf.lycee.gestion.domain.enums.Sexe;
import bf.lycee.gestion.domain.enums.StatutInscription;
import bf.lycee.gestion.imports.LecteurTableur;
import bf.lycee.gestion.imports.LecteurTableur.Ligne;
import bf.lycee.gestion.imports.RapportImport;
import bf.lycee.gestion.repository.AnneeScolaireRepository;
import bf.lycee.gestion.repository.ClasseRepository;
import bf.lycee.gestion.repository.EleveRepository;
import bf.lycee.gestion.repository.InscriptionRepository;
import bf.lycee.gestion.web.EleveDto.CorrectionCategorie;
import bf.lycee.gestion.web.EleveDto.EleveVue;
import bf.lycee.gestion.web.EleveDto.InscriptionManuelle;
import bf.lycee.gestion.web.EleveDto.InscriptionVue;
import bf.lycee.gestion.web.EleveDto.LigneImport;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Fiches des élèves (SF-07), inscriptions et import par classe (SF-08). */
@Service
@Transactional
public class EleveService {

    private static final Map<String, List<String>> COLONNES = Map.of(
            "matricule", List.of("mle", "numero matricule"),
            "nom", List.of("noms"),
            "prenom", List.of("prenoms", "prénom(s)"),
            "date_naissance", List.of("date de naissance", "naissance", "ne le", "né(e) le"),
            "sexe", List.of("genre"),
            "nom_parent", List.of("parent", "nom du parent", "tuteur", "parent ou tuteur"),
            "contact_parent", List.of("contact", "contact du parent", "telephone parent", "téléphone", "tel"),
            "redoublant", List.of("redouble", "redoublant(e)"));
    private static final List<String> OBLIGATOIRES = List.of("matricule", "nom", "prenom", "date_naissance", "sexe");
    private static final List<DateTimeFormatter> FORMATS_DATE = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            // STRICT : sans cela, une date impossible comme 31/02/2014 serait ramenée au 28/02
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT));

    private final EleveRepository eleves;
    private final InscriptionRepository inscriptions;
    private final ClasseRepository classes;
    private final AnneeScolaireRepository annees;
    private final LecteurTableur lecteur;

    public EleveService(EleveRepository eleves, InscriptionRepository inscriptions, ClasseRepository classes,
                        AnneeScolaireRepository annees, LecteurTableur lecteur) {
        this.eleves = eleves;
        this.inscriptions = inscriptions;
        this.classes = classes;
        this.annees = annees;
        this.lecteur = lecteur;
    }

    @Transactional(readOnly = true)
    public List<EleveVue> rechercher(String terme) {
        String nettoye = terme == null ? "" : terme.trim();
        return eleves.rechercher(nettoye, PageRequest.of(0, 50)).stream().map(EleveVue::de).toList();
    }

    @Transactional(readOnly = true)
    public List<InscriptionVue> inscriptionsDeLaClasse(Long classeId) {
        return inscriptions.findByClasseIdOrderByEleveNomAscElevePrenomAsc(classeId).stream()
                .map(InscriptionVue::de).toList();
    }

    /** Inscription manuelle : crée la fiche élève si le matricule est inconnu. */
    public InscriptionVue inscrire(Long classeId, InscriptionManuelle saisie) {
        Classe classe = classe(classeId);
        AnneeScolaire annee = classe.getAnneeScolaire();
        String matricule = saisie.matricule().trim();
        Eleve eleve = eleves.findByMatricule(matricule).orElse(null);
        if (eleve != null) {
            verifierNonInscrit(List.of(eleve), annee).forEach((id, nomClasse) -> {
                throw new RegleMetierException("L'élève " + matricule + " est déjà inscrit en " + nomClasse
                        + " pour " + annee.getLibelle());
            });
        }
        long effectif = inscriptions.countByClasseIdAndStatut(classeId, StatutInscription.ACTIVE);
        if (effectif + 1 > classe.getEffectifMax()) {
            throw new RegleMetierException("La classe " + classe.getNom() + " est complète ("
                    + classe.getEffectifMax() + " élèves)");
        }
        if (eleve == null) {
            eleve = eleves.save(nouvelEleve(matricule, saisie.nom(), saisie.prenom(), saisie.dateNaissance(),
                    saisie.sexe(), saisie.nomParent(), saisie.contactParent()));
        }
        Inscription inscription = inscriptions.save(
                nouvelleInscription(eleve, classe, categorie(eleve, anneePrecedente(annee)), saisie.redoublant()));
        return InscriptionVue.de(inscription);
    }

    /** Correction manuelle de la catégorie nouveau / ancien, avec motif (SF-23). */
    public InscriptionVue corrigerCategorie(Long inscriptionId, CorrectionCategorie correction) {
        Inscription inscription = inscriptions.findById(inscriptionId)
                .orElseThrow(() -> new IntrouvableException("Inscription introuvable : " + inscriptionId));
        inscription.setCategorie(correction.categorie());
        inscription.setCategorieForcee(true);
        inscription.setMotifCategorie(correction.motif().trim());
        return InscriptionVue.de(inscriptions.save(inscription));
    }

    /**
     * Import de la liste d'une classe. En simulation, le fichier est seulement
     * analysé. Si une ligne est en erreur, rien n'est importé.
     */
    public RapportImport<LigneImport> importer(Long classeId, MultipartFile fichier, boolean simulation) {
        Classe classe = classe(classeId);
        AnneeScolaire annee = classe.getAnneeScolaire();
        Optional<AnneeScolaire> precedente = anneePrecedente(annee);
        List<Ligne> lignes = lecteur.lire(fichier, COLONNES, OBLIGATOIRES);

        Map<String, Eleve> connus = new HashMap<>();
        eleves.findByMatriculeIn(lignes.stream().map(l -> l.get("matricule")).toList())
                .forEach(e -> connus.put(e.getMatricule(), e));
        Map<Long, String> dejaInscrits = verifierNonInscrit(connus.values(), annee);

        List<RapportImport.Erreur> erreurs = new ArrayList<>();
        List<LigneImport> apercu = new ArrayList<>();
        List<Inscription> aCreer = new ArrayList<>();
        Set<String> matriculesVus = new HashSet<>();

        for (Ligne ligne : lignes) {
            String matricule = ligne.get("matricule");
            String nom = ligne.get("nom");
            String prenom = ligne.get("prenom");
            List<String> problemes = new ArrayList<>();
            Eleve connu = connus.get(matricule);

            if (matricule.isEmpty()) {
                problemes.add("matricule manquant");
            } else if (matricule.length() > 30) {
                problemes.add("matricule trop long (30 caractères maximum)");
            } else if (!matriculesVus.add(matricule)) {
                problemes.add("matricule " + matricule + " en double dans le fichier");
            } else if (connu != null && dejaInscrits.containsKey(connu.getId())) {
                problemes.add("élève déjà inscrit en " + dejaInscrits.get(connu.getId()) + " pour "
                        + annee.getLibelle());
            }
            if (nom.isEmpty()) {
                problemes.add("nom manquant");
            }
            if (prenom.isEmpty()) {
                problemes.add("prénom manquant");
            }
            LocalDate naissance = date(ligne.get("date_naissance"));
            if (naissance == null) {
                problemes.add(ligne.get("date_naissance").isEmpty() ? "date de naissance manquante"
                        : "date de naissance invalide : " + ligne.get("date_naissance")
                        + " (attendu : JJ/MM/AAAA)");
            } else if (!naissance.isBefore(LocalDate.now())) {
                problemes.add("date de naissance dans le futur");
            }
            Sexe sexe = sexe(ligne.get("sexe"));
            if (sexe == null) {
                problemes.add(ligne.get("sexe").isEmpty() ? "sexe manquant"
                        : "sexe invalide : " + ligne.get("sexe") + " (attendu : M ou F)");
            }
            Boolean redoublant = ouiNon(ligne.get("redoublant"));
            if (redoublant == null) {
                problemes.add("valeur invalide pour redoublant : " + ligne.get("redoublant")
                        + " (attendu : oui ou non)");
            }

            CategorieEleve categorie = connu != null ? categorie(connu, precedente) : CategorieEleve.NOUVEAU;
            apercu.add(new LigneImport(ligne.numero(), matricule, nom, prenom,
                    naissance != null ? naissance.toString() : ligne.get("date_naissance"),
                    sexe != null ? sexe.name() : ligne.get("sexe"),
                    Boolean.TRUE.equals(redoublant), connu != null, categorie.name()));

            if (problemes.isEmpty()) {
                Eleve eleve = connu != null ? connu
                        : nouvelEleve(matricule, nom, prenom, naissance, sexe,
                        ligne.get("nom_parent"), ligne.get("contact_parent"));
                aCreer.add(nouvelleInscription(eleve, classe, categorie, redoublant));
            } else {
                erreurs.add(new RapportImport.Erreur(ligne.numero(), String.join(" ; ", problemes)));
            }
        }

        List<String> avertissements = new ArrayList<>();
        long effectif = inscriptions.countByClasseIdAndStatut(classeId, StatutInscription.ACTIVE);
        if (effectif + lignes.size() > classe.getEffectifMax()) {
            erreurs.add(new RapportImport.Erreur(1, "L'import porterait l'effectif de " + classe.getNom() + " à "
                    + (effectif + lignes.size()) + " élèves, au-delà du maximum de " + classe.getEffectifMax()));
        }
        long anciens = apercu.stream().filter(l -> "ANCIEN".equals(l.categorie())).count();
        if (precedente.isEmpty()) {
            avertissements.add("Aucune année scolaire précédente n'est enregistrée : "
                    + "tous les élèves sont considérés comme nouveaux");
        } else {
            avertissements.add(anciens + " ancien(s) élève(s) et " + (apercu.size() - anciens)
                    + " nouveau(x), d'après les inscriptions de " + precedente.get().getLibelle());
        }

        boolean importe = false;
        if (!simulation && erreurs.isEmpty()) {
            for (Inscription inscription : aCreer) {
                if (inscription.getEleve().getId() == null) {
                    eleves.save(inscription.getEleve());
                }
                inscriptions.save(inscription);
            }
            importe = true;
        }
        return new RapportImport<>(simulation, importe, lignes.size(), erreurs, avertissements, apercu);
    }

    // ---------- Règles ----------

    /** ANCIEN si l'élève était inscrit dans l'établissement l'année précédente, sinon NOUVEAU (SF-23). */
    private CategorieEleve categorie(Eleve eleve, Optional<AnneeScolaire> anneePrecedente) {
        boolean ancien = eleve.getId() != null && anneePrecedente.isPresent()
                && inscriptions.existsByEleveIdAndAnneeScolaireIdAndStatut(
                eleve.getId(), anneePrecedente.get().getId(), StatutInscription.ACTIVE);
        return ancien ? CategorieEleve.ANCIEN : CategorieEleve.NOUVEAU;
    }

    private Optional<AnneeScolaire> anneePrecedente(AnneeScolaire annee) {
        return annees.findFirstByDateDebutBeforeOrderByDateDebutDesc(annee.getDateDebut());
    }

    /** @return pour les élèves déjà inscrits cette année : identifiant de l'élève → nom de sa classe */
    private Map<Long, String> verifierNonInscrit(java.util.Collection<Eleve> candidats, AnneeScolaire annee) {
        Map<Long, String> dejaInscrits = new HashMap<>();
        List<Long> ids = candidats.stream().map(Eleve::getId).toList();
        if (!ids.isEmpty()) {
            for (Inscription i : inscriptions.findByAnneeScolaireIdAndEleveIdIn(annee.getId(), ids)) {
                dejaInscrits.put(i.getEleve().getId(), i.getClasse().getNom());
            }
        }
        return dejaInscrits;
    }

    private Classe classe(Long id) {
        return classes.findById(id).orElseThrow(() -> new IntrouvableException("Classe introuvable : " + id));
    }

    private static Eleve nouvelEleve(String matricule, String nom, String prenom, LocalDate naissance, Sexe sexe,
                                     String nomParent, String contactParent) {
        Eleve eleve = new Eleve();
        eleve.setMatricule(matricule.trim());
        eleve.setNom(nom.trim());
        eleve.setPrenom(prenom.trim());
        eleve.setDateNaissance(naissance);
        eleve.setSexe(sexe);
        eleve.setNomParent(vide(nomParent));
        eleve.setContactParent(vide(contactParent));
        return eleve;
    }

    private static Inscription nouvelleInscription(Eleve eleve, Classe classe, CategorieEleve categorie,
                                                   boolean redoublant) {
        Inscription inscription = new Inscription();
        inscription.setEleve(eleve);
        inscription.setClasse(classe);
        inscription.setAnneeScolaire(classe.getAnneeScolaire());
        inscription.setCategorie(categorie);
        inscription.setCategorieForcee(false);
        inscription.setRedoublant(redoublant);
        inscription.setDateInscription(LocalDate.now());
        inscription.setStatut(StatutInscription.ACTIVE);
        return inscription;
    }

    private static LocalDate date(String texte) {
        for (DateTimeFormatter format : FORMATS_DATE) {
            try {
                return LocalDate.parse(texte, format);
            } catch (DateTimeParseException e) {
                // format suivant
            }
        }
        return null;
    }

    private static Sexe sexe(String texte) {
        return switch (LecteurTableur.normaliser(texte)) {
            case "m", "masculin", "g", "garcon", "h", "homme" -> Sexe.M;
            case "f", "feminin", "fille", "femme" -> Sexe.F;
            default -> null;
        };
    }

    /** Colonne facultative : vide = non. Renvoie {@code null} si la valeur est incompréhensible. */
    private static Boolean ouiNon(String texte) {
        return switch (LecteurTableur.normaliser(texte)) {
            case "", "non", "n", "0", "false", "faux" -> Boolean.FALSE;
            case "oui", "o", "1", "true", "vrai", "x" -> Boolean.TRUE;
            default -> null;
        };
    }

    private static String vide(String texte) {
        return (texte == null || texte.isBlank()) ? null : texte.trim();
    }
}
