package bf.lycee.gestion.imports;

import java.util.List;

/**
 * Résultat d'un import en deux étapes : d'abord une simulation (analyse et
 * aperçu, rien n'est enregistré), puis l'import définitif. Si une seule
 * ligne est en erreur, aucune ligne n'est importée.
 *
 * @param simulation vrai s'il s'agissait d'une simple analyse
 * @param importe vrai si les lignes ont été enregistrées en base
 * @param <T> type des lignes de l'aperçu
 */
public record RapportImport<T>(
        boolean simulation,
        boolean importe,
        int totalLignes,
        List<Erreur> erreurs,
        List<String> avertissements,
        List<T> lignes) {

    public record Erreur(int ligne, String message) {
    }
}
