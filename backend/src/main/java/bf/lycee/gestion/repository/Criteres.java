package bf.lycee.gestion.repository;

import bf.lycee.gestion.domain.Eleve;
import bf.lycee.gestion.domain.Inscription;
import bf.lycee.gestion.domain.Personnel;
import bf.lycee.gestion.domain.enums.RolePersonnel;
import bf.lycee.gestion.domain.enums.Sexe;
import bf.lycee.gestion.domain.enums.StatutInscription;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Critères de recherche composables pour les listes paginées. Chaque méthode
 * rend {@code null} quand le critère n'est pas renseigné : la composition par
 * {@link Specification#allOf} ignore alors ce critère.
 */
public final class Criteres {

    private Criteres() {
    }

    // ---------- Élèves ----------

    /** Cherche le terme dans le matricule, le nom, le prénom et le nom du parent. */
    public static Specification<Eleve> eleveRecherche(String terme) {
        String motif = motif(terme);
        if (motif == null) {
            return null;
        }
        return (racine, requete, cb) -> cb.or(
                like(cb, racine.get("matricule"), motif),
                like(cb, racine.get("nom"), motif),
                like(cb, racine.get("prenom"), motif),
                like(cb, racine.get("nomParent"), motif));
    }

    public static Specification<Eleve> eleveSexe(Sexe sexe) {
        return sexe == null ? null : (racine, requete, cb) -> cb.equal(racine.get("sexe"), sexe);
    }

    /** Élèves nés dans l'intervalle, bornes incluses. */
    public static Specification<Eleve> eleveNeEntre(LocalDate debut, LocalDate fin) {
        if (debut == null && fin == null) {
            return null;
        }
        return (racine, requete, cb) -> {
            Path<LocalDate> naissance = racine.get("dateNaissance");
            if (debut == null) {
                return cb.lessThanOrEqualTo(naissance, fin);
            }
            if (fin == null) {
                return cb.greaterThanOrEqualTo(naissance, debut);
            }
            return cb.between(naissance, debut, fin);
        };
    }

    // ---------- Personnel ----------

    /** Cherche le terme dans le matricule, le nom, le prénom, l'adresse et le téléphone. */
    public static Specification<Personnel> personnelRecherche(String terme) {
        String motif = motif(terme);
        if (motif == null) {
            return null;
        }
        return (racine, requete, cb) -> cb.or(
                like(cb, racine.get("matricule"), motif),
                like(cb, racine.get("nom"), motif),
                like(cb, racine.get("prenom"), motif),
                like(cb, racine.get("email"), motif),
                like(cb, racine.get("telephone"), motif));
    }

    public static Specification<Personnel> personnelRole(RolePersonnel role) {
        return role == null ? null : (racine, requete, cb) -> cb.equal(racine.get("role"), role);
    }

    public static Specification<Personnel> personnelActif(Boolean actif) {
        return actif == null ? null : (racine, requete, cb) -> cb.equal(racine.get("actif"), actif);
    }

    // ---------- Inscriptions ----------

    public static Specification<Inscription> inscriptionClasse(UUID classeId) {
        return classeId == null
                ? null
                : (racine, requete, cb) -> cb.equal(racine.get("classe").get("id"), classeId);
    }

    /** Cherche le terme dans le matricule, le nom et le prénom de l'élève inscrit. */
    public static Specification<Inscription> inscriptionRecherche(String terme) {
        String motif = motif(terme);
        if (motif == null) {
            return null;
        }
        return (racine, requete, cb) -> {
            Path<Object> eleve = racine.get("eleve");
            return cb.or(
                    like(cb, eleve.get("matricule"), motif),
                    like(cb, eleve.get("nom"), motif),
                    like(cb, eleve.get("prenom"), motif));
        };
    }

    public static Specification<Inscription> inscriptionStatut(StatutInscription statut) {
        return statut == null ? null : (racine, requete, cb) -> cb.equal(racine.get("statut"), statut);
    }

    public static Specification<Inscription> inscriptionRedoublant(Boolean redoublant) {
        return redoublant == null
                ? null
                : (racine, requete, cb) -> cb.equal(racine.get("redoublant"), redoublant);
    }

    // ---------- Outils ----------

    /** Motif « %terme% » en minuscules, ou {@code null} si le terme est vide. */
    private static String motif(String terme) {
        if (terme == null || terme.isBlank()) {
            return null;
        }
        return "%" + terme.trim().toLowerCase() + "%";
    }

    private static jakarta.persistence.criteria.Predicate like(
            jakarta.persistence.criteria.CriteriaBuilder cb, Expression<String> champ, String motif) {
        return cb.like(cb.lower(champ), motif);
    }
}
