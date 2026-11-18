package bf.lycee.gestion.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Une page de résultats, telle que l'interface la consomme. On n'expose pas
 * {@link Page} directement : sa sérialisation JSON n'est pas stable d'une
 * version de Spring à l'autre, et elle transporte bien plus que nécessaire.
 *
 * @param contenu les éléments de la page
 * @param page    numéro de la page, à partir de 0
 * @param taille  nombre d'éléments demandés par page
 * @param total   nombre total d'éléments, toutes pages confondues
 * @param pages   nombre de pages
 */
public record PageVue<T>(List<T> contenu, int page, int taille, long total, int pages) {

    /** Page issue de la base, les entités étant converties au passage. */
    public static <E, T> PageVue<T> de(Page<E> page, Function<E, T> conversion) {
        return new PageVue<>(page.getContent().stream().map(conversion).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /**
     * Page découpée en mémoire, pour les listes qu'il faut calculer en entier
     * avant de pouvoir les filtrer (les impayés, dont le reste à payer ne se
     * connaît qu'après rafraîchissement des statuts).
     */
    public static <T> PageVue<T> de(List<T> tout, Pageable pageable) {
        if (pageable.isUnpaged()) {
            return new PageVue<>(tout, 0, tout.size(), tout.size(), 1);
        }
        int taille = pageable.getPageSize();
        int debut = (int) Math.min(pageable.getOffset(), tout.size());
        int fin = Math.min(debut + taille, tout.size());
        int pages = (int) Math.ceil((double) tout.size() / taille);
        return new PageVue<>(tout.subList(debut, fin), pageable.getPageNumber(), taille,
                tout.size(), Math.max(pages, 1));
    }
}
