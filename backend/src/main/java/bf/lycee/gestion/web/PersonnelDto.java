package bf.lycee.gestion.web;

import bf.lycee.gestion.domain.Personnel;
import bf.lycee.gestion.domain.enums.RolePersonnel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class PersonnelDto {

    private PersonnelDto() {
    }

    public record Creation(
            @NotBlank @Size(max = 30) String matricule,
            @NotBlank @Size(max = 100) String nom,
            @NotBlank @Size(max = 100) String prenom,
            @NotBlank @Email @Size(max = 150) String email,
            @Size(max = 30) String telephone,
            @NotNull RolePersonnel role) {
    }

    public record Modification(
            @NotBlank @Size(max = 100) String nom,
            @NotBlank @Size(max = 100) String prenom,
            @NotBlank @Email @Size(max = 150) String email,
            @Size(max = 30) String telephone,
            @NotNull RolePersonnel role) {
    }

    public record Vue(UUID id, String matricule, String nom, String prenom, String email, String telephone,
                      RolePersonnel role, boolean actif, boolean compteCree) {

        public static Vue de(Personnel p) {
            return new Vue(p.getId(), p.getMatricule(), p.getNom(), p.getPrenom(), p.getEmail(),
                    p.getTelephone(), p.getRole(), p.isActif(), p.getKeycloakId() != null);
        }
    }

    /** Résultat d'une création : la fiche, et un avertissement si le compte de connexion n'a pas pu être créé. */
    public record Resultat(Vue personnel, String avertissement) {
    }

    /** Ligne d'aperçu d'un import. */
    public record LigneImport(int ligne, String matricule, String nom, String prenom, String email,
                              String telephone, String role) {
    }
}
