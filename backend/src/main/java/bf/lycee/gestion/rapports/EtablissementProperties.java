package bf.lycee.gestion.rapports;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Entête des documents : ce qui ne dépend pas des données imprimées. Ces
 * valeurs sont passées à Jasper en paramètres (et non en champs), de sorte
 * qu'un maquettiste puisse les déplacer dans le .jrxml sans toucher au code.
 *
 * <p>Se règle dans {@code application.yml}, sous {@code lycee.etablissement}.
 */
@ConfigurationProperties(prefix = "lycee.etablissement")
public record EtablissementProperties(String nom, String ministere, String direction, String adresse,
                                      String telephone, String email, String devise,
                                      String logo) {

    public EtablissementProperties {
        nom = nom == null ? "Lycée" : nom;
        ministere = ministere == null ? "" : ministere;
        direction = direction == null ? "" : direction;
        adresse = adresse == null ? "" : adresse;
        telephone = telephone == null ? "" : telephone;
        email = email == null ? "" : email;
        devise = devise == null ? "" : devise;
        logo = logo == null ? "" : logo;
    }
}
