package bf.lycee.gestion.web;

import bf.lycee.gestion.domain.Eleve;
import bf.lycee.gestion.domain.Inscription;
import bf.lycee.gestion.domain.enums.CategorieEleve;
import bf.lycee.gestion.domain.enums.Sexe;
import bf.lycee.gestion.domain.enums.StatutInscription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class EleveDto {

    private EleveDto() {
    }

    /** Inscription manuelle d'un élève dans une classe (l'élève est créé s'il n'existe pas). */
    public record InscriptionManuelle(
            @NotBlank @Size(max = 30) String matricule,
            @NotBlank @Size(max = 100) String nom,
            @NotBlank @Size(max = 100) String prenom,
            @NotNull @Past LocalDate dateNaissance,
            @NotNull Sexe sexe,
            @Size(max = 150) String nomParent,
            @Size(max = 50) String contactParent,
            boolean redoublant) {
    }

    /** Correction manuelle de la catégorie (SF-23) : le motif est obligatoire. */
    public record CorrectionCategorie(
            @NotNull CategorieEleve categorie,
            @NotBlank @Size(max = 255) String motif) {
    }

    public record EleveVue(Long id, String matricule, String nom, String prenom, LocalDate dateNaissance,
                           Sexe sexe, String nomParent, String contactParent) {

        public static EleveVue de(Eleve e) {
            return new EleveVue(e.getId(), e.getMatricule(), e.getNom(), e.getPrenom(), e.getDateNaissance(),
                    e.getSexe(), e.getNomParent(), e.getContactParent());
        }
    }

    public record InscriptionVue(Long id, EleveVue eleve, Long classeId, CategorieEleve categorie,
                                 boolean categorieForcee, String motifCategorie, boolean redoublant,
                                 LocalDate dateInscription, StatutInscription statut) {

        public static InscriptionVue de(Inscription i) {
            return new InscriptionVue(i.getId(), EleveVue.de(i.getEleve()), i.getClasse().getId(),
                    i.getCategorie(), i.isCategorieForcee(), i.getMotifCategorie(), i.isRedoublant(),
                    i.getDateInscription(), i.getStatut());
        }
    }

    /** Ligne d'aperçu d'un import d'élèves. */
    public record LigneImport(int ligne, String matricule, String nom, String prenom, String dateNaissance,
                              String sexe, boolean redoublant, boolean dejaConnu, String categorie) {
    }
}
