package bf.lycee.gestion.service;

import bf.lycee.gestion.domain.Personnel;
import bf.lycee.gestion.domain.enums.RolePersonnel;
import bf.lycee.gestion.imports.LecteurTableur;
import bf.lycee.gestion.imports.LecteurTableur.Ligne;
import bf.lycee.gestion.imports.RapportImport;
import bf.lycee.gestion.provisionnement.ProvisionnementComptes;
import bf.lycee.gestion.repository.PersonnelRepository;
import bf.lycee.gestion.web.PersonnelDto.Creation;
import bf.lycee.gestion.web.PersonnelDto.LigneImport;
import bf.lycee.gestion.web.PersonnelDto.Modification;
import bf.lycee.gestion.web.PersonnelDto.Resultat;
import bf.lycee.gestion.web.PersonnelDto.Vue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Référentiel du personnel (SF-05) et importation en masse (SF-06). */
@Service
@Transactional
public class PersonnelService {

    private static final Logger LOG = LoggerFactory.getLogger(PersonnelService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final Map<String, List<String>> COLONNES = Map.of(
            "matricule", List.of("mle", "numero matricule"),
            "nom", List.of("noms"),
            "prenom", List.of("prenoms", "prénom(s)"),
            "email", List.of("e-mail", "mail", "courriel", "adresse email"),
            "telephone", List.of("tel", "téléphone", "contact"),
            "role", List.of("rôle", "fonction", "profil"));
    private static final List<String> OBLIGATOIRES = List.of("matricule", "nom", "prenom", "email", "role");

    private final PersonnelRepository personnels;
    private final ProvisionnementComptes provisionnement;
    private final LecteurTableur lecteur;

    public PersonnelService(PersonnelRepository personnels, ProvisionnementComptes provisionnement,
                            LecteurTableur lecteur) {
        this.personnels = personnels;
        this.provisionnement = provisionnement;
        this.lecteur = lecteur;
    }

    @Transactional(readOnly = true)
    public List<Vue> lister() {
        return personnels.findAllByOrderByNomAscPrenomAsc().stream().map(Vue::de).toList();
    }

    public Resultat creer(Creation saisie) {
        String matricule = saisie.matricule().trim();
        String email = saisie.email().trim().toLowerCase(Locale.ROOT);
        if (personnels.existsByMatriculeIgnoreCase(matricule)) {
            throw new RegleMetierException("Le matricule " + matricule + " est déjà utilisé");
        }
        if (personnels.existsByEmailIgnoreCase(email)) {
            throw new RegleMetierException("L'adresse " + email + " est déjà utilisée");
        }
        Personnel personnel = nouveau(matricule, saisie.nom(), saisie.prenom(), email, saisie.telephone(),
                saisie.role());
        personnels.save(personnel);
        String avertissement = provisionner(personnel);
        return new Resultat(Vue.de(personnel), avertissement);
    }

    /**
     * Modifie une fiche, rôle compris. Le rôle est répercuté sur le compte de
     * connexion quand il existe : sans cela, la personne garderait ses anciens
     * droits jusqu'à ce qu'on reprenne le compte à la main dans Keycloak.
     */
    public Vue modifier(UUID id, Modification saisie) {
        Personnel personnel = trouver(id);
        String email = saisie.email().trim().toLowerCase();
        if (!EMAIL.matcher(email).matches()) {
            throw new SaisieInvalideException("Adresse électronique invalide : " + email);
        }
        if (!personnel.getEmail().equalsIgnoreCase(email)
                && personnels.existsByEmailIgnoreCase(email)) {
            throw new RegleMetierException("Une fiche porte déjà l'adresse " + email);
        }

        RolePersonnel ancienRole = personnel.getRole();
        personnel.setNom(saisie.nom().trim());
        personnel.setPrenom(saisie.prenom().trim());
        personnel.setEmail(email);
        personnel.setTelephone(vide(saisie.telephone()));
        personnel.setRole(saisie.role());
        personnels.save(personnel);

        if (saisie.role() != ancienRole && personnel.getKeycloakId() != null) {
            try {
                provisionnement.definirRole(personnel.getKeycloakId(), saisie.role());
            } catch (RuntimeException e) {
                LOG.warn("Changement de rôle impossible dans Keycloak pour {}", personnel.getEmail(), e);
                throw new RegleMetierException("Le rôle n'a pas pu être changé sur le compte de "
                        + "connexion dans Keycloak ; réessayez plus tard");
            }
        }
        return Vue.de(personnel);
    }

    /** Active ou désactive une fiche et, s'il existe, son compte de connexion. */
    public Vue definirActivation(UUID id, boolean actif) {
        Personnel personnel = trouver(id);
        personnel.setActif(actif);
        personnels.save(personnel);
        if (personnel.getKeycloakId() != null) {
            try {
                provisionnement.definirActivation(personnel.getKeycloakId(), actif);
            } catch (RuntimeException e) {
                LOG.warn("Activation du compte Keycloak impossible pour {}", personnel.getEmail(), e);
                throw new RegleMetierException(
                        "Le compte de connexion n'a pas pu être mis à jour dans Keycloak ; réessayez plus tard");
            }
        }
        return Vue.de(personnel);
    }

    /** Relance la création du compte de connexion lorsqu'elle avait échoué. */
    public Resultat creerCompte(UUID id) {
        Personnel personnel = trouver(id);
        String avertissement = provisionner(personnel);
        return new Resultat(Vue.de(personnel), avertissement);
    }

    /**
     * Import en masse. En simulation, le fichier est seulement analysé.
     * Si une ligne est en erreur, rien n'est importé.
     */
    public RapportImport<LigneImport> importer(MultipartFile fichier, boolean simulation) {
        List<Ligne> lignes = lecteur.lire(fichier, COLONNES, OBLIGATOIRES);
        List<RapportImport.Erreur> erreurs = new ArrayList<>();
        List<LigneImport> apercu = new ArrayList<>();

        Set<String> matriculesEnBase = personnels
                .findByMatriculeIn(lignes.stream().map(l -> l.get("matricule")).toList()).stream()
                .map(p -> p.getMatricule().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        Set<String> emailsEnBase = personnels
                .findByEmailIn(lignes.stream().map(l -> l.get("email").toLowerCase(Locale.ROOT)).toList()).stream()
                .map(p -> p.getEmail().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        Set<String> matriculesVus = new HashSet<>();
        Set<String> emailsVus = new HashSet<>();
        List<Personnel> aCreer = new ArrayList<>();

        for (Ligne ligne : lignes) {
            String matricule = ligne.get("matricule");
            String nom = ligne.get("nom");
            String prenom = ligne.get("prenom");
            String email = ligne.get("email").toLowerCase(Locale.ROOT);
            String telephone = ligne.get("telephone");
            String roleSaisi = ligne.get("role");
            List<String> problemes = new ArrayList<>();

            if (matricule.isEmpty()) {
                problemes.add("matricule manquant");
            } else if (matricule.length() > 30) {
                problemes.add("matricule trop long (30 caractères maximum)");
            } else if (!matriculesVus.add(matricule.toLowerCase(Locale.ROOT))) {
                problemes.add("matricule " + matricule + " en double dans le fichier");
            } else if (matriculesEnBase.contains(matricule.toLowerCase(Locale.ROOT))) {
                problemes.add("matricule " + matricule + " déjà enregistré");
            }
            if (nom.isEmpty()) {
                problemes.add("nom manquant");
            }
            if (prenom.isEmpty()) {
                problemes.add("prénom manquant");
            }
            if (email.isEmpty()) {
                problemes.add("email manquant");
            } else if (!EMAIL.matcher(email).matches() || email.length() > 150) {
                problemes.add("email invalide : " + email);
            } else if (!emailsVus.add(email)) {
                problemes.add("email " + email + " en double dans le fichier");
            } else if (emailsEnBase.contains(email)) {
                problemes.add("email " + email + " déjà enregistré");
            }
            RolePersonnel role = role(roleSaisi);
            if (role == null) {
                problemes.add(roleSaisi.isEmpty() ? "rôle manquant" : "rôle inconnu : " + roleSaisi
                        + " (attendu : DIRECTEUR, CENSEUR, ENSEIGNANT, INTENDANT, ECONOME ou SURVEILLANT)");
            }

            apercu.add(new LigneImport(ligne.numero(), matricule, nom, prenom, email, telephone,
                    role != null ? role.name() : roleSaisi));
            if (problemes.isEmpty()) {
                aCreer.add(nouveau(matricule, nom, prenom, email, telephone, role));
            } else {
                erreurs.add(new RapportImport.Erreur(ligne.numero(), String.join(" ; ", problemes)));
            }
        }

        List<String> avertissements = new ArrayList<>();
        boolean importe = false;
        if (!simulation && erreurs.isEmpty()) {
            personnels.saveAll(aCreer);
            importe = true;
            if (provisionnement.actif()) {
                int echecs = 0;
                for (Personnel personnel : aCreer) {
                    if (provisionner(personnel) != null) {
                        echecs++;
                    }
                }
                if (echecs > 0) {
                    avertissements.add(echecs + " compte(s) de connexion n'ont pas pu être créés dans Keycloak ; "
                            + "relancez la création depuis la fiche des personnes concernées");
                }
            } else {
                avertissements.add("La création automatique des comptes Keycloak est désactivée : "
                        + "les comptes de connexion sont à créer manuellement");
            }
        }
        return new RapportImport<>(simulation, importe, lignes.size(), erreurs, avertissements, apercu);
    }

    /** @return un avertissement si le compte n'a pas pu être créé, sinon {@code null} */
    private String provisionner(Personnel personnel) {
        if (!provisionnement.actif() || personnel.getKeycloakId() != null) {
            return null;
        }
        try {
            UUID keycloakId = provisionnement.creerCompte(personnel);
            personnel.setKeycloakId(keycloakId);
            personnels.save(personnel);
            return null;
        } catch (RuntimeException e) {
            LOG.warn("Création du compte Keycloak impossible pour {}", personnel.getEmail(), e);
            return "La fiche est enregistrée, mais le compte de connexion n'a pas pu être créé dans Keycloak";
        }
    }

    private Personnel trouver(UUID id) {
        return personnels.findById(id)
                .orElseThrow(() -> new IntrouvableException("Membre du personnel introuvable : " + id));
    }

    private static Personnel nouveau(String matricule, String nom, String prenom, String email,
                                     String telephone, RolePersonnel role) {
        Personnel personnel = new Personnel();
        personnel.setMatricule(matricule.trim());
        personnel.setNom(nom.trim());
        personnel.setPrenom(prenom.trim());
        personnel.setEmail(email);
        personnel.setTelephone(vide(telephone));
        personnel.setRole(role);
        personnel.setActif(true);
        return personnel;
    }

    private static RolePersonnel role(String saisie) {
        String normalise = LecteurTableur.normaliser(saisie);
        for (RolePersonnel role : RolePersonnel.values()) {
            if (role.name().toLowerCase(Locale.ROOT).equals(normalise)) {
                return role;
            }
        }
        return switch (normalise) {
            case "proviseur", "directrice" -> RolePersonnel.DIRECTEUR;
            case "professeur", "prof", "enseignante" -> RolePersonnel.ENSEIGNANT;
            case "caissier", "caissiere" -> RolePersonnel.ECONOME;
            case "intendante" -> RolePersonnel.INTENDANT;
            case "surveillante" -> RolePersonnel.SURVEILLANT;
            default -> null;
        };
    }

    private static String vide(String texte) {
        return (texte == null || texte.isBlank()) ? null : texte.trim();
    }
}
