package bf.lycee.gestion.rapports;

import java.util.Locale;

/**
 * Formats de sortie proposés par le moteur d'édition. Le format est choisi à
 * l'appel : le même fichier Jasper produit indifféremment un PDF, un classeur
 * Excel, un document Word, une page HTML ou un fichier CSV.
 */
public enum FormatRapport {

    PDF("pdf", "application/pdf"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    HTML("html", "text/html;charset=UTF-8"),
    CSV("csv", "text/csv;charset=UTF-8");

    private final String extension;
    private final String typeMime;

    FormatRapport(String extension, String typeMime) {
        this.extension = extension;
        this.typeMime = typeMime;
    }

    public String extension() {
        return extension;
    }

    public String typeMime() {
        return typeMime;
    }

    /** Les formats tabulaires se prêtent mal aux mises en page à bandes. */
    public boolean estTabulaire() {
        return this == XLSX || this == CSV;
    }

    /** Accepte « pdf », « PDF », « xlsx »… et refuse clairement le reste. */
    public static FormatRapport depuis(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return PDF;
        }
        try {
            return valueOf(valeur.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Format de sortie inconnu : " + valeur
                    + ". Formats acceptés : pdf, xlsx, docx, html, csv");
        }
    }
}
