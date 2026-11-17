package bf.lycee.gestion.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Montant écrit en toutes lettres, pour les reçus (SF-30). */
public final class MontantEnLettres {

    private static final String[] UNITES = {
            "zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf",
            "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize",
            "dix-sept", "dix-huit", "dix-neuf"
    };

    private static final String[] DIZAINES = {
            "", "", "vingt", "trente", "quarante", "cinquante", "soixante"
    };

    private MontantEnLettres() {
    }

    /** « 125 000 » devient « cent vingt-cinq mille francs CFA ». */
    public static String francsCfa(BigDecimal montant) {
        long valeur = montant.setScale(0, RoundingMode.HALF_UP).longValueExact();
        return convertir(valeur) + " francs CFA";
    }

    public static String convertir(long valeur) {
        if (valeur < 0) {
            return "moins " + convertir(-valeur);
        }
        if (valeur < 1000) {
            return centaines((int) valeur, false);
        }
        StringBuilder texte = new StringBuilder();
        long reste = valeur;

        long milliards = reste / 1_000_000_000L;
        reste %= 1_000_000_000L;
        if (milliards > 0) {
            texte.append(groupe((int) milliards, "milliard"));
        }

        long millions = reste / 1_000_000L;
        reste %= 1_000_000L;
        if (millions > 0) {
            ajouter(texte, groupe((int) millions, "million"));
        }

        long milliers = reste / 1000L;
        reste %= 1000L;
        if (milliers > 0) {
            // « mille » est invariable et ne se dit pas « un mille ».
            ajouter(texte, milliers == 1 ? "mille" : centaines((int) milliers, true) + " mille");
        }

        if (reste > 0) {
            ajouter(texte, centaines((int) reste, false));
        }
        return texte.toString();
    }

    private static String groupe(int nombre, String nom) {
        return centaines(nombre, false) + " " + nom + (nombre > 1 ? "s" : "");
    }

    private static void ajouter(StringBuilder texte, String mots) {
        if (!texte.isEmpty()) {
            texte.append(' ');
        }
        texte.append(mots);
    }

    /**
     * Nombre de 0 à 999. « cent » et « quatre-vingt » restent invariables
     * devant « mille » : deux cent mille, mais deux cents.
     */
    private static String centaines(int nombre, boolean devantMille) {
        int centaines = nombre / 100;
        int reste = nombre % 100;
        if (centaines == 0) {
            return deuxChiffres(reste, devantMille);
        }
        String debut = centaines == 1 ? "cent" : UNITES[centaines] + " cent";
        if (reste == 0) {
            return (centaines == 1 || devantMille) ? debut : debut + "s";
        }
        return debut + " " + deuxChiffres(reste, devantMille);
    }

    /** Nombre de 0 à 99. */
    private static String deuxChiffres(int nombre, boolean devantMille) {
        if (nombre < 20) {
            return UNITES[nombre];
        }
        int dizaine = nombre / 10;
        int unite = nombre % 10;

        if (dizaine == 7 || dizaine == 9) {
            String base = dizaine == 7 ? "soixante" : "quatre-vingt";
            if (dizaine == 7 && unite == 1) {
                return "soixante et onze";
            }
            return base + "-" + UNITES[10 + unite];
        }
        if (dizaine == 8) {
            if (unite == 0) {
                return devantMille ? "quatre-vingt" : "quatre-vingts";
            }
            return "quatre-vingt-" + UNITES[unite];
        }
        if (unite == 0) {
            return DIZAINES[dizaine];
        }
        if (unite == 1) {
            return DIZAINES[dizaine] + " et un";
        }
        return DIZAINES[dizaine] + "-" + UNITES[unite];
    }
}
