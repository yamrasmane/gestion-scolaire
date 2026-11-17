package bf.lycee.gestion.service;

import bf.lycee.gestion.domain.Evaluation;
import bf.lycee.gestion.domain.Note;
import bf.lycee.gestion.domain.enums.CategorieEvaluation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Moyenne d'une matière (SF-13). Une note est valide si elle est saisie et que
 * l'élève n'est pas marqué absent ; les notes sont ramenées sur 20 selon le
 * barème de l'épreuve, et les moyennes arrondies à deux décimales.
 */
public final class CalculMoyennes {

    public static final BigDecimal VINGT = new BigDecimal("20");

    private CalculMoyennes() {
    }

    /**
     * @param notesDevoirs     notes de devoirs valides, ramenées sur 20
     * @param moyenneDevoirs   moyenne des devoirs, {@code null} si aucun devoir valide
     * @param composition      note de composition sur 20, {@code null} si absente
     * @param moyenne          moyenne de la matière, {@code null} si non classé
     * @param absentComposition composition prévue mais non notée
     */
    public record ResultatMatiere(List<BigDecimal> notesDevoirs, BigDecimal moyenneDevoirs,
                                  BigDecimal composition, BigDecimal moyenne,
                                  boolean absentComposition) {

        public boolean nonClasse() {
            return moyenne == null;
        }
    }

    /**
     * @param notes           notes de l'élève pour une matière et un trimestre
     * @param avecComposition option Composition applicable au trimestre et au cycle (SF-03)
     * @param poidsComposition poids de la composition face à la moyenne des devoirs
     */
    public static ResultatMatiere matiere(List<Note> notes, boolean avecComposition,
                                          BigDecimal poidsComposition) {
        List<BigDecimal> devoirs = new ArrayList<>();
        BigDecimal composition = null;
        boolean compositionPrevue = false;

        for (Note note : notes) {
            Evaluation evaluation = note.getEvaluation();
            boolean estComposition = evaluation.getCategorie() == CategorieEvaluation.COMPOSITION;
            if (estComposition) {
                compositionPrevue = true;
            }
            if (note.isAbsent() || note.getValeur() == null) {
                continue;
            }
            BigDecimal sur20 = surVingt(note.getValeur(), evaluation.getBareme());
            if (estComposition) {
                composition = sur20;
            } else {
                devoirs.add(sur20);
            }
        }

        BigDecimal moyenneDevoirs = devoirs.isEmpty() ? null : moyenne(devoirs);
        BigDecimal moyenne;

        if (!avecComposition || composition == null) {
            // Sans composition, ou composition manquante : seuls les devoirs comptent.
            moyenne = moyenneDevoirs;
        } else if (moyenneDevoirs == null) {
            // Aucun devoir valide : la composition fait la moyenne.
            moyenne = composition;
        } else {
            BigDecimal poids = poidsComposition == null ? BigDecimal.ONE : poidsComposition;
            moyenne = moyenneDevoirs.add(composition.multiply(poids))
                    .divide(BigDecimal.ONE.add(poids), 2, RoundingMode.HALF_UP);
        }

        boolean absentComposition = avecComposition && compositionPrevue && composition == null;
        return new ResultatMatiere(devoirs, moyenneDevoirs, composition, moyenne, absentComposition);
    }

    /** Moyenne générale pondérée par les coefficients ; les matières NC sont exclues (SF-14). */
    public static BigDecimal generale(BigDecimal totalPoints, BigDecimal totalCoefficients) {
        if (totalCoefficients == null || totalCoefficients.signum() == 0) {
            return null;
        }
        return totalPoints.divide(totalCoefficients, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal moyenne(List<BigDecimal> valeurs) {
        BigDecimal somme = valeurs.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return somme.divide(new BigDecimal(valeurs.size()), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal surVingt(BigDecimal valeur, BigDecimal bareme) {
        if (bareme == null || bareme.signum() == 0 || bareme.compareTo(VINGT) == 0) {
            return valeur;
        }
        return valeur.multiply(VINGT).divide(bareme, 2, RoundingMode.HALF_UP);
    }

    /**
     * Rangs par compétition standard (SF-16) : deux ex æquo en 2e place sont
     * tous deux 2e, et le suivant est 4e. Les valeurs doivent être triées par
     * ordre décroissant ; {@code null} (non classé) reçoit un rang vide.
     */
    public static List<Rang> rangs(List<BigDecimal> moyennesTriees) {
        List<Rang> resultat = new ArrayList<>();
        int position = 0;
        BigDecimal precedente = null;
        int rangCourant = 0;

        for (BigDecimal moyenne : moyennesTriees) {
            position++;
            if (moyenne == null) {
                resultat.add(new Rang(null, false));
                continue;
            }
            if (precedente == null || moyenne.compareTo(precedente) != 0) {
                rangCourant = position;
                precedente = moyenne;
            }
            resultat.add(new Rang(rangCourant, false));
        }

        // Marque les ex æquo : un rang attribué plusieurs fois.
        for (int i = 0; i < resultat.size(); i++) {
            Integer rang = resultat.get(i).valeur();
            if (rang == null) {
                continue;
            }
            boolean partage = resultat.stream()
                    .filter(r -> rang.equals(r.valeur()))
                    .count() > 1;
            if (partage) {
                resultat.set(i, new Rang(rang, true));
            }
        }
        return resultat;
    }

    public record Rang(Integer valeur, boolean exAequo) {
    }

    /** Décision proposée au conseil de classe d'après la moyenne générale (SF-18). */
    public static String mention(BigDecimal moyenneGenerale) {
        if (moyenneGenerale == null) {
            return null;
        }
        if (moyenneGenerale.compareTo(new BigDecimal("16")) >= 0) {
            return "Félicitations";
        }
        if (moyenneGenerale.compareTo(new BigDecimal("14")) >= 0) {
            return "Encouragements";
        }
        if (moyenneGenerale.compareTo(new BigDecimal("12")) >= 0) {
            return "Tableau d'honneur";
        }
        if (moyenneGenerale.compareTo(new BigDecimal("8")) < 0) {
            return "Avertissement";
        }
        return null;
    }
}
